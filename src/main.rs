use std::{fs, io};
use std::fs::create_dir_all;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use downloader::Verification;
use hex::ToHex;
use serde_json::{Map, Value};
use sha1::{Digest, Sha1};
use subprocess::{Exec, Redirection};

use crate::error::{Error, Result};
use crate::metadata::{JavaHome, Version};
use crate::utils::{download_file, ProgressReporter};
use crate::versions::VERSIONS;

mod versions;
mod utils;
mod metadata;
mod error;

#[tokio::main]
async fn main() -> Result<()> {
    {
        let path = Path::new("work");

        if !path.exists() {
            create_dir_all(&path)?;
        }
    }

    println!("Fetching metadata...");
    let metadata = utils::get_mc_version_metadata().await?;
    println!("Metadata fetched!");
    let java = utils::get_java_home()?;

    for version in VERSIONS {
        println!("Working on version: {}", version);
        let mut path = PathBuf::from("work");
        path.push(&version);

        if !path.exists() {
            create_dir_all(&path)?;
        }

        let version_info = metadata.find_version(version).ok_or(Error::Custom(format!("Version {} not found", version)))?;
        work(version_info, &path, &java).await?;
        break;
    }

    Ok(())
}

async fn work(version: &Version, path: &Path, java_home: &JavaHome) -> Result<()> {
    let server_json = version.url.to_string();

    {
        let value = reqwest::get(&server_json)
            .await?
            .json::<Value>()
            .await?;
        let data = value
            .as_object()
            .ok_or(Error::Custom("Failed to parse server json".to_string()))?;
        download_server(&version, data, &path).await?;
    }

    println!("Running {}", &version.id);
    println!("Checking java version...");
    let java = get_java_for_version(&version.id, java_home)?;
    println!("Using java: {:?} runtime", java);

    {
        let eula_path = path.join("eula.txt");
        if !eula_path.exists() {
            let mut file = std::fs::File::create(&eula_path)?;
            file.write_all(b"eula=true")?;
        }
    }

    let status = Exec::shell(format!("{} -Xmx2048M -Xms2048M -jar server.jar nogui", java.display()))
        .cwd(path)
        .stdout(Redirection::Pipe)
        .stderr(Redirection::Pipe)
        .stdin(Redirection::Pipe)
        .join()?;

    if !status.success() {
        return Err(Error::Custom(String::from("Failed to run server")));
    }

    Ok(())
}

async fn download_server(version: &Version, server_json: &Map<String, Value>, path: &Path) -> Result<()> {
    let download_data = server_json.get("downloads")
        .map(|x| x.get("server"))
        .flatten()
        .map(|x| x.as_object())
        .flatten()
        .ok_or(Error::Custom("Failed to get server download".to_string()))?;
    let uri = download_data.get("url")
        .map(|x| x.as_str())
        .flatten()
        .map(|x| x.to_owned())
        .ok_or(Error::Custom("Failed to get server download url".to_string()))?;
    let checksum = download_data.get("sha1")
        .map(|x| x.as_str().to_owned())
        .flatten()
        .map(|x| x.to_owned())
        .ok_or(Error::Custom("Failed to get server download sha1".to_string()))?;
    let size = download_data.get("size")
        .map(|x| x.as_u64())
        .flatten()
        .map(|x| x.to_owned())
        .ok_or(Error::Custom("Failed to get server download size".to_string()))?;

    let server_path = path.join("server.jar");

    if server_path.exists() {
        let metadata = fs::metadata(&server_path)?;
        if metadata.len() == size {
            let mut file = fs::File::open(&server_path)?;
            let mut hasher = Sha1::new();
            let _ = io::copy(&mut file, &mut hasher)?;
            let hash = hasher.finalize();

            let hash = hash.iter().encode_hex::<String>();
            if hash == checksum {
                return Ok(());
            }
        }

        fs::remove_file(&server_path)?;
    }

    println!("Downloading server.jar for version: {}", &version.id);

    let path = path.to_owned();
    tokio::task::spawn_blocking(move || {
        download_file(&path, &uri, None, Some(Arc::new(move |path, _| {
            let mut file = fs::File::open(path).unwrap();
            let mut hasher = Sha1::new();
            let _ = io::copy(&mut file, &mut hasher);
            let hash = hasher.finalize();

            if hash.iter().encode_hex::<String>() == checksum {
                Verification::Ok
            } else {
                Verification::Failed
            }
        })), Some(Arc::new(ProgressReporter::default())))
    }).await??;

    Ok(())
}

fn get_java_for_version(version: &str, java_home: &JavaHome) -> Result<PathBuf> {
    let jvm = if version.starts_with("1.8") || version.starts_with("1.9") || version.starts_with("1.10") || version.starts_with("1.11") || version.starts_with("1.12") {
        java_home._8.as_str()
    } else if version.starts_with("1.13") || version.starts_with("1.14") || version.starts_with("1.15") || version.starts_with("1.16") {
        java_home._11.as_str()
    } else if version.starts_with("1.17")  {
        java_home._17.as_str()
    } else {
        java_home._21.as_str()
    };

    let mut path = PathBuf::from(jvm);
    path.push("jre/bin/java");

    Ok(path)
}
