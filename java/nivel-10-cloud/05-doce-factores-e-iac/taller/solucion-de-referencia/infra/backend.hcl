# Conexión al estado remoto (LocalStack). En AWS real: se quitan endpoints y las claves ficticias (se usa un rol), y se añade
# encrypt = true con una clave KMS; el bucket de estado se crea aparte, con versionado y acceso restringido.
bucket                      = "tofu-estado"
key                         = "pedidos/estado.tfstate"
workspace_key_prefix        = "ambientes"
region                      = "us-east-1"
access_key                  = "test"
secret_key                  = "test"
skip_credentials_validation = true
skip_metadata_api_check     = true
skip_requesting_account_id  = true
skip_region_validation      = true
use_path_style              = true
use_lockfile                = true
endpoints                   = { s3 = "http://localhost:4566" }
