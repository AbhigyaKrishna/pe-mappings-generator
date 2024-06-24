use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "PascalCase")]
pub struct JavaHome {
    pub _21: String,
    pub _17: String,
    pub _11: String,
    pub _8: String
}
