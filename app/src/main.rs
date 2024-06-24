use std::fs;
use std::fs::{create_dir_all, File};
use std::io::Write;
use std::path::{Path, PathBuf};

use subprocess::{Exec, Redirection};

use crate::download::download_build_tool;
use crate::error::{Error, Result};
use crate::metadata::JavaHome;
use crate::utils::{get_java_for_version, get_java_home};
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

    let java = get_java_home()?;

    println!("Building agent...");
    let agent = build_agent().await?;
    download_build_tool(&path).await?;

    for version in VERSIONS {
        println!("Working on version: {}", version);

        work(&version, &path, &java, &agent).await?;
        break;
    }

    Ok(())
}

async fn work(version: &str, path: &Path, java_home: &JavaHome, agent: &Path) -> Result<()> {
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
        download_server(&path, &version, &java).await?;
    }

    println!("Running {}", &version);

    {
        let eula_path = work_dir.join("eula.txt");
        if !eula_path.exists() {
            let mut file = File::create(&eula_path)?;
            file.write_all(b"eula=true")?;
        }
    }

    let agent = fs::canonicalize(agent)?;
    let status = Exec::shell(format!("{} -Xmx2048M -Xms2048M -XX:+StartAttachListener -javaagent:{} -jar server.jar nogui", java.display(), agent.display()))
        .cwd(&work_dir)
        .stdout(Redirection::Merge)
        .stderr(Redirection::Merge)
        .join()?;

    if !status.success() {
        return Err(Error::Custom(String::from("Failed to run server")));
    }

    Ok(())
}

async fn build_agent() -> Result<PathBuf> {
    let status = Exec::shell("./gradlew agent:build")
        .stdout(Redirection::Merge)
        .stderr(Redirection::Merge)
        .join()?;

    if !status.success() {
        return Err(Error::Custom(String::from("Failed to build project...")));
    }

    Ok(PathBuf::from("./agent/build/libs/agent.jar"))
}

async fn download_server(path: &Path, version: &str, java: &Path) -> Result<()> {
    let status = Exec::shell(format!("{} -Xmx2048M -Xms2048M -jar BuildTools.jar --rev {} --o ./{} --final-name server.jar --disable-java-check --nogui", java.display(), version, version))
        .cwd(&path)
        .stdout(Redirection::Merge)
        .stderr(Redirection::Merge)
        .join()?;

    if !status.success() {
        return Err(Error::Custom(String::from("Failed to install server.jar")));
    }

    Ok(())
}
