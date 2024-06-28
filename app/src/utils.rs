use std::fs::File;
use std::io;
use std::io::BufReader;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use downloader::{Verification, Verify};
use downloader::progress::Reporter;
use hex::ToHex;
use sha1::{Digest, Sha1};

use crate::metadata::JavaHome;
use crate::Result;

pub fn get_java_home() -> Result<JavaHome> {
    Ok(serde_json::from_reader(BufReader::new(File::open("java_home.json")?))?)
}

pub fn get_java_for_version(version: &str, java_home: &JavaHome) -> Result<PathBuf> {
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
    path.push("bin/java");

    Ok(path)
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

pub fn get_sha1_verify(checksum: String) -> Verify {
    Arc::new(move |path, _| {
        let mut file = File::open(path).unwrap();
        let mut hasher = Sha1::new();
        let _ = io::copy(&mut file, &mut hasher);
        let hash = hasher.finalize();

        if hash.iter().encode_hex::<String>() == checksum {
            Verification::Ok
        } else {
            Verification::Failed
        }
    })
}
