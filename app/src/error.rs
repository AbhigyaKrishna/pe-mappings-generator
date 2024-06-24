#[derive(Debug)]
pub enum Error {
    IO(std::io::Error),
    Request(reqwest::Error),
    Serde(serde_json::Error),
    Downloader(downloader::Error),
    SubProcess(subprocess::PopenError),
    Concurrency(tokio::task::JoinError),
    Custom(String),
}

impl From<std::io::Error> for Error {
    fn from(e: std::io::Error) -> Self {
        Error::IO(e)
    }
}

impl From<reqwest::Error> for Error {
    fn from(e: reqwest::Error) -> Self {
        Error::Request(e)
    }
}

impl From<serde_json::Error> for Error {
    fn from(e: serde_json::Error) -> Self {
        Error::Serde(e)
    }
}

impl From<downloader::Error> for Error {
    fn from(e: downloader::Error) -> Self {
        Error::Downloader(e)
    }
}

impl From<tokio::task::JoinError> for Error {
    fn from(e: tokio::task::JoinError) -> Self {
        Error::Concurrency(e)
    }
}

impl From<subprocess::PopenError> for Error {
    fn from(e: subprocess::PopenError) -> Self {
        Error::SubProcess(e)
    }
}

pub type Result<T> = std::result::Result<T, Error>;
