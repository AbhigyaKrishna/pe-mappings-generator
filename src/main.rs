use std::fs::{create_dir_all, File};
use std::io::Write;
use std::path::{Path, PathBuf};

use hex::ToHex;
use sha1::Digest;
use subprocess::{Exec, Redirection};

use crate::download::download_build_tool;
use crate::error::{Error, Result};
use crate::metadata::{JavaHome};
use crate::versions::VERSIONS;

mod versions;
mod utils;
mod metadata;
mod error;
mod download;

#[tokio::main]
async fn main() -> Result<()> {
    let path = Path::new("work");

    if !path.exists() {
        create_dir_all(&path)?;
    }

    let java = utils::get_java_home()?;

    download_build_tool(path).await?;

    for version in VERSIONS {
        println!("Working on version: {}", version);

        work(&version, &path, &java).await?;
        break;
    }

    Ok(())
}

async fn work(version: &str, path: &Path, java_home: &JavaHome) -> Result<()> {
    let work_dir = path.join(&version);
    if !work_dir.exists() {
        create_dir_all(&work_dir)?;
    }

    println!("Getting version build...");
    println!("Checking java version...");
    let java = get_java_for_version(&version, java_home)?;
    println!("Using java: {:?} runtime", java);

    let server_jar = work_dir.join("server.jar");
    if !server_jar.exists() {
        download_server(&work_dir, &version, &java).await?;
    }

    println!("Running {}", &version);

    {
        let eula_path = work_dir.join("eula.txt");
        if !eula_path.exists() {
            let mut file = File::create(&eula_path)?;
            file.write_all(b"eula=true")?;
        }
    }

    let status = Exec::shell(format!("{} -Xmx2048M -Xms2048M -jar server.jar nogui", java.display()))
        .cwd(&work_dir)
        .stdout(Redirection::Merge)
        .stderr(Redirection::Merge)
        .join()?;

    if !status.success() {
        return Err(Error::Custom(String::from("Failed to run server")));
    }

    Ok(())
}

async fn download_server(path: &Path, version: &str, java: &Path) -> Result<()> {
    let status = Exec::shell(format!("{} -jar -Xmx2048M -Xms2048M BuildTools.jar --rev {} --o ./{} --final-name server.jar --disable-java-check --nogui", java.display(), version, version))
        .cwd(&path)
        .stdout(Redirection::Merge)
        .stderr(Redirection::Merge)
        .join()?;

    if !status.success() {
        return Err(Error::Custom(String::from("Failed to install server.jar")));
    }

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
