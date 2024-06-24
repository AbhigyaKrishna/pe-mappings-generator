use std::{fs, io};
use std::fs::File;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;
use downloader::{Download, Downloader, DownloadSummary, Progress, Verify};
use hex::ToHex;
use sha1::{Digest, Sha1};
use crate::error::Result;
use crate::utils::{get_sha1_verify, ProgressReporter};

pub fn download_file(path: &Path, uri: &str, file_name: Option<&str>, verify: Option<Verify>, progress: Option<Progress>) -> Result<Vec<downloader::Result<DownloadSummary>>> {
    let mut downloader = Downloader::builder()
        .download_folder(path)
        .timeout(Duration::from_secs(30))
        .build()
        .unwrap();

    let mut download = Download::new(uri);
    if let Some(path) = file_name {
        download = download.file_name(Path::new(path));
    }
    if let Some(verify) = verify {
        download = download.verify(verify);
    }
    if let Some(progress) = progress {
        download = download.progress(progress);
    }

    Ok(downloader.download(&[download])?)
}

pub async fn download_file_with_defaults(path: &Path, uri: &str, file_name: &str, checksum: Option<String>) -> Result<()> {
    let file_path = path.join(file_name);

    if file_path.exists() {
        if let Some(s) = checksum.clone() {
            let mut file = File::open(&file_path)?;
            let mut hasher = Sha1::new();
            let _ = io::copy(&mut file, &mut hasher)?;
            let hash = hasher.finalize();

            let hash = hash.iter().encode_hex::<String>();
            if hash == s {
                return Ok(());
            }
        }

        fs::remove_file(&file_name)?;
    }

    println!("Downloading {}", file_name);
    let path = path.to_owned();
    let uri = uri.to_owned();
    let file_name = file_name.to_owned();
    tokio::task::spawn_blocking(move || {
        download_file(&path, &uri, Some(&file_name), checksum.map(|s| get_sha1_verify(s)), Some(Arc::new(ProgressReporter::default())))
    }).await??;

    Ok(())
}

pub async fn download_build_tool(path: &Path) -> Result<PathBuf> {
    download_file_with_defaults(
        path,
        "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar",
        "BuildTools.jar",
        Some("0e07a7db1d937f9727e661a427ac4421407eb3c5".to_string())
    ).await?;

    Ok(path.join("BuildTools.jar"))
}