use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug)]
pub struct McMetaData {
    pub latest: Latest,
    pub versions: Vec<Version>
}

#[derive(Serialize, Deserialize, Debug)]
pub struct Latest {
    pub release: String,
    pub snapshot: String
}

#[derive(Serialize, Deserialize, Debug)]
pub struct Version {
    pub id: String,
    #[serde(rename = "type")]
    pub ver_type: String,
    pub url: String,
    pub time: DateTime<Utc>,
    #[serde(rename = "releaseTime")]
    pub release_time: DateTime<Utc>
}

impl McMetaData {
    pub fn find_version(&self, version: &str) -> Option<&Version> {
        self.versions.iter().find(|v| v.id == version)
    }
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "PascalCase")]
pub struct JavaHome {
    pub _21: String,
    pub _17: String,
    pub _11: String,
    pub _8: String
}
