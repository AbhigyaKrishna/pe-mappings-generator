use std::fs::File;
use std::io::BufReader;
use std::path::Path;
use std::sync::Mutex;
use std::time::Duration;
use crate::Result;

use downloader::{Download, Downloader, DownloadSummary, Progress, Verify};
use downloader::progress::Reporter;
use crate::metadata::{JavaHome, McMetaData};

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

pub async fn get_mc_version_metadata() -> Result<McMetaData> {
    Ok(reqwest::get("https://launchermeta.mojang.com/mc/game/version_manifest.json")
        .await?
        .json::<McMetaData>()
        .await?)
}

pub fn get_java_home() -> Result<JavaHome> {
    Ok(serde_json::from_reader(BufReader::new(File::open("java_home.json")?))?)
}

#[derive(Debug, Default)]
pub struct ProgressReporter {
    total: Mutex<Option<u64>>
}

impl Reporter for ProgressReporter {
    fn setup(&self, max_progress: Option<u64>, message: &str) {
        println!("{}", message);
        if let Some(prog) = max_progress {
            self.total.lock().unwrap().replace(prog);
        }
    }

    fn progress(&self, current: u64) {
        let guard = self.total.lock().unwrap();
        if guard.is_some() {
            println!("{}\\{}", current, guard.unwrap());
        } else {
            println!("{}", current);
        }
    }

    fn set_message(&self, message: &str) {
        println!("{}", message);
    }

    fn done(&self) {
        println!("Download Complete!");
    }
}
