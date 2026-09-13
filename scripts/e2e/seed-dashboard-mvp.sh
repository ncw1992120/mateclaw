#!/usr/bin/env bash
set -euo pipefail

: "${MATECLAW_E2E_WORKSPACE_ID:?set MATECLAW_E2E_WORKSPACE_ID}"
aloudata_dataset_id="${MATECLAW_E2E_ALOUDATA_DATASET_ID:-}"
aloudata_mode="${MATECLAW_E2E_ALOUDATA_MODE:-}"

BASE_URL="${MATECLAW_E2E_API_BASE_URL:-http://127.0.0.1:18189/dataagent/api}"
FIXTURE="${MATECLAW_E2E_ORDERS_FIXTURE:-mateclaw-dataagent-ui/e2e/fixtures/orders.csv}"
STATE_FILE="${MATECLAW_E2E_STATE_FILE:-/tmp/mateclaw-dashboard-mvp-state-${MATECLAW_E2E_WORKSPACE_ID}.json}"
AUTH_HEADER="Authorization: Bearer ${MATECLAW_E2E_TOKEN:-}"
WORKSPACE_HEADER="X-Workspace-Id: ${MATECLAW_E2E_WORKSPACE_ID}"

command -v curl >/dev/null || { echo 'curl is required' >&2; exit 1; }
command -v jq >/dev/null || { echo 'jq is required' >&2; exit 1; }
command -v node >/dev/null || { echo 'node is required to create RSA-OAEP test envelopes' >&2; exit 1; }
test -f "$FIXTURE" || { echo "fixture not found: $FIXTURE" >&2; exit 1; }

api() {
  local method="$1" path="$2" body="${3:-}"
  if [[ -n "$body" ]]; then
    curl --fail-with-body --silent --show-error -X "$method" \
      -H "$AUTH_HEADER" -H "$WORKSPACE_HEADER" -H 'Content-Type: application/json' \
      "$BASE_URL$path" --data "$body"
  else
    curl --fail-with-body --silent --show-error -X "$method" \
      -H "$AUTH_HEADER" -H "$WORKSPACE_HEADER" "$BASE_URL$path"
  fi
}

id_from() { jq -er '.data.id // .data.datasetId // .data.objectId'; }

public_key=$(curl --fail --silent --show-error "$BASE_URL/v1/auth/pubkey" | jq -er '.data.publicKey')
encrypt_field() {
  PUBLIC_KEY="$public_key" PLAIN_VALUE="$1" node - <<'NODE'
const crypto = require('crypto')
const blob = Buffer.from(`${Date.now()}:${process.env.PLAIN_VALUE}`, 'utf8').toString('base64')
const encrypted = crypto.publicEncrypt({
  key: process.env.PUBLIC_KEY,
  padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
  oaepHash: 'sha256',
}, Buffer.from(blob, 'ascii'))
process.stdout.write(encrypted.toString('base64'))
NODE
}

if [[ -z "${MATECLAW_E2E_TOKEN:-}" ]]; then
  : "${MATECLAW_E2E_USERNAME:?set MATECLAW_E2E_USERNAME when MATECLAW_E2E_TOKEN is unset}"
  : "${MATECLAW_E2E_PASSWORD:?set MATECLAW_E2E_PASSWORD when MATECLAW_E2E_TOKEN is unset}"
  login_payload=$(jq -cn \
    --arg username "$MATECLAW_E2E_USERNAME" \
    --arg password "$(encrypt_field "$MATECLAW_E2E_PASSWORD")" \
    '{username:$username,password:$password,channel:"local"}')
  MATECLAW_E2E_TOKEN=$(curl --fail-with-body --silent --show-error -X POST \
    -H 'Content-Type: application/json' "$BASE_URL/v1/auth/login" \
    --data "$login_payload" | jq -er '.data.token')
fi
: "${MATECLAW_E2E_TOKEN:?set MATECLAW_E2E_TOKEN or local login credentials}"
AUTH_HEADER="Authorization: Bearer ${MATECLAW_E2E_TOKEN}"
WORKSPACE_HEADER="X-Workspace-Id: ${MATECLAW_E2E_WORKSPACE_ID}"

echo 'Checking DataAgent health...'
curl --fail --silent --show-error "$BASE_URL/actuator/health" >/dev/null

jdbc_ds=$(api POST /v1/datasources "$(jq -cn \
  --arg password "$(encrypt_field "${MATECLAW_E2E_JDBC_PASSWORD:-e2e-user-password}")" \
  '{name:"E2E JDBC Orders",description:"dashboard MVP E2E",sourceType:"mysql",host:"e2e-mysql",port:3306,databaseName:"mateclaw",username:"mateclaw",password:$password,enabled:true,metaShared:true}')" | id_from)
jdbc_dataset=$(api POST /v1/datasets "$(jq -cn --arg ds "$jdbc_ds" \
  '{name:"E2E JDBC Orders Dataset",description:"dashboard MVP JDBC source",sourceDefinition:{sourceType:"JDBC_SQL",datasourceId:($ds|tonumber),sql:"SELECT id, order_date, region, status, amount FROM orders"}}')" | id_from)

http_connection_params=$(jq -cn '{apiDefinitions:{orders:{endpoint:"https://e2e-http:8443/orders",method:"GET",allowedHosts:["e2e-http"],allowedQueryParams:["status"],paginationMode:"none",resultPath:"$.data"}}}')
http_ds=$(api POST /v1/datasources "$(jq -cn \
  --arg connectionParams "$http_connection_params" \
  '{name:"E2E HTTP Orders",description:"dashboard MVP E2E",sourceType:"api",host:"e2e-http",port:8443,enabled:true,metaShared:true,connectionParams:$connectionParams}')" | id_from)
http_dataset=$(api POST /v1/datasets "$(jq -cn --arg ds "$http_ds" \
  '{name:"E2E HTTP Orders Dataset",description:"dashboard MVP HTTP source",sourceDefinition:{sourceType:"HTTP_API",datasourceId:($ds|tonumber),apiDefinitionId:"orders"}}')" | id_from)

file_ref=$(curl --fail-with-body --silent --show-error -X POST \
  -H "$AUTH_HEADER" -H "$WORKSPACE_HEADER" \
  -F "file=@${FIXTURE};type=text/csv" "$BASE_URL/v1/dataset-files" | jq -er '.data')
file_object_id=$(jq -er '.objectId' <<<"$file_ref")
file_dataset=$(api POST /v1/datasets "$(jq -cn --arg object "$file_object_id" \
  '{name:"E2E File Orders Dataset",description:"dashboard MVP file source",sourceDefinition:{sourceType:"FILE",objectId:$object,format:"CSV",schemaVersion:1}}')" | id_from)

dashboard_schema() {
  local left_id="$1" left_name="$2" right_id="$3" right_name="$4" script="$5"
  jq -cn --arg l "$left_id" --arg ln "$left_name" --arg r "$right_id" --arg rn "$right_name" --arg script "$script" '{
    version:"1.1",
    pages:[{id:"e2e-page",name:"E2E",components:[{id:"e2e-table",type:"table",title:"E2E Result",position:{x:0,y:0,w:12,h:6},renderType:"table",dataSource:{datasourceId:"",metrics:[],dimensions:[],filters:[],limit:100}}]}],
    datasetInputs:[{datasetId:$l,inputName:$ln},{datasetId:$r,inputName:$rn}],
    script:$script,
    parameters:[],
    executionPolicy:{timeoutSeconds:60,maxOutputBytes:50000},
    scriptBindings:[{componentId:"e2e-table",renderType:"table"}]
  }'
}

echarts_dashboard_schema() {
  local dataset_id="$1" script="$2"
  jq -cn --arg dataset "$dataset_id" --arg script "$script" '{
    version:"1.1",
    pages:[{id:"e2e-chart-page",name:"E2E Chart",components:[{id:"e2e-chart",type:"chart",title:"E2E Script Chart",position:{x:0,y:0,w:12,h:6},renderType:"echarts",dataSource:{datasourceId:"",metrics:[],dimensions:[],filters:[],limit:100}}]}],
    datasetInputs:[{datasetId:$dataset,inputName:"jdbc_orders"}],
    script:$script,
    parameters:[],
    executionPolicy:{timeoutSeconds:60,maxOutputBytes:50000},
    scriptBindings:[{componentId:"e2e-chart",renderType:"echarts"}]
  }'
}

jdbc_aloudata_script=$'left = datasets.read(input_name="jdbc_orders")\nright = datasets.read(input_name="aloudata_metrics", filters=[{"field": "region", "operator": "eq", "value": "east"}])\nresult = left.to_polars().to_dicts() + right.to_polars().to_dicts()'
api_file_script=$'paid_filter = {"field": "status", "operator": "eq", "value": "PAID"}\napi_rows = datasets.read(input_name="api_orders", filters=[paid_filter])\nfile_rows = datasets.read(input_name="file_orders", filters=[paid_filter])\nresult = api_rows.to_polars().to_dicts() + file_rows.to_polars().to_dicts()'

multi_dashboard=""
aloudata_ds=""
if [[ "$aloudata_mode" == "simulation" ]]; then
  aloudata_connection_params=$(jq -cn '{anymetricsHost:"https://e2e-http",semanticHost:"https://e2e-http",anymetricsPort:8443,semanticPort:8443,authType:"UID"}')
  aloudata_ds=$(api POST /v1/datasources "$(jq -cn \
    --arg connectionParams "$aloudata_connection_params" \
    --arg password "$(encrypt_field local-simulation-auth)" \
    '{name:"E2E Aloudata Simulation",description:"dashboard MVP simulated Aloudata",sourceType:"aloudata",host:"e2e-http",port:8443,productHost:"https://e2e-http",semanticHost:"https://e2e-http",username:"local-tenant",password:$password,enabled:true,metaShared:true,connectionParams:$connectionParams}')" | id_from)
  aloudata_dataset_id=$(api POST /v1/datasets "$(jq -cn --arg ds "$aloudata_ds" \
    '{name:"E2E Aloudata Metrics Dataset",description:"dashboard MVP simulated metric view",sourceDefinition:{sourceType:"ALOUDATA_ANALYSIS_VIEW",datasourceId:($ds|tonumber),analysisViewId:"local_sales_view"}}')" | id_from)
elif [[ -n "$aloudata_dataset_id" ]]; then
  aloudata_mode="live"
fi
if [[ -n "$aloudata_dataset_id" ]]; then
  multi_schema=$(dashboard_schema "$jdbc_dataset" jdbc_orders "$aloudata_dataset_id" aloudata_metrics "$jdbc_aloudata_script")
  multi_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$multi_schema" '{name:"E2E JDBC + Aloudata Dashboard",description:"dashboard MVP real dual-source flow",schemaJson:$schema}')" | id_from)
else
  echo 'Aloudata dataset ID not provided; JDBC + Aloudata dashboard will not be seeded (set MATECLAW_E2E_ALOUDATA_MODE=simulation or provide MATECLAW_E2E_ALOUDATA_DATASET_ID).' >&2
fi
api_file_schema=$(dashboard_schema "$http_dataset" api_orders "$file_dataset" file_orders "$api_file_script")
api_file_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$api_file_schema" '{name:"E2E API + File Dashboard",description:"dashboard MVP API and file flow",schemaJson:$schema}')" | id_from)
echarts_script=$'rows = datasets.read(input_name="jdbc_orders", filters=[{"field": "region", "operator": "eq", "value": "east"}])\nresult = rows.to_polars().select(["region", "amount"]).to_dicts()'
echarts_schema=$(echarts_dashboard_schema "$jdbc_dataset" "$echarts_script")
echarts_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$echarts_schema" '{name:"E2E ECharts Binding Dashboard",description:"dashboard MVP ECharts script binding flow",schemaJson:$schema}')" | id_from)
compat_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema '{"version":"1.0","components":[]}' '{name:"E2E Legacy Compatibility Dashboard",description:"dashboard MVP legacy schema",schemaJson:$schema}')" | id_from)
error_schema=$(jq -cn --arg dataset "$http_dataset" '{version:"1.1",pages:[{id:"e2e-error-page",name:"E2E Error",components:[]}],datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:"raise RuntimeError(\"e2e expected script failure\")",parameters:[],executionPolicy:{timeoutSeconds:60,maxOutputBytes:50000}}')
error_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$error_schema" '{name:"E2E Script Error Dashboard",description:"dashboard MVP controlled script failure",schemaJson:$schema}')" | id_from)
cancel_schema=$(jq -cn --arg dataset "$http_dataset" '{version:"1.1",pages:[{id:"e2e-cancel-page",name:"E2E Cancel",components:[]}],datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:"import time\ntime.sleep(30)\nresult = []",parameters:[],executionPolicy:{timeoutSeconds:60,maxOutputBytes:50000}}')
cancel_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$cancel_schema" '{name:"E2E Cancellation Dashboard",description:"dashboard MVP controlled long-running script",schemaJson:$schema}')" | id_from)
timeout_schema=$(jq -cn --arg dataset "$http_dataset" '{version:"1.1",pages:[{id:"e2e-timeout-page",name:"E2E Timeout",components:[]}],datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:"import time\ntime.sleep(5)\nresult = []",parameters:[],executionPolicy:{timeoutSeconds:1,maxOutputBytes:50000}}')
timeout_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$timeout_schema" '{name:"E2E Script Timeout Dashboard",description:"dashboard MVP controlled script timeout",schemaJson:$schema}')" | id_from)
resource_schema=$(jq -cn --arg dataset "$http_dataset" '{version:"1.1",pages:[{id:"e2e-resource-page",name:"E2E Resource",components:[]}],datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:"print(\"x\" * 60000)\nresult = []",parameters:[],executionPolicy:{timeoutSeconds:60,maxOutputBytes:50000}}')
resource_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$resource_schema" '{name:"E2E Resource Limit Dashboard",description:"dashboard MVP controlled stdout limit",schemaJson:$schema}')" | id_from)
large_schema=$(jq -cn --arg dataset "$http_dataset" '{version:"1.1",pages:[{id:"e2e-large-page",name:"E2E Large Result",components:[]}],datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:"result = [{\"id\": i, \"payload\": \"x\" * 2000} for i in range(1000)]",parameters:[],executionPolicy:{timeoutSeconds:60,maxOutputBytes:50000}}')
large_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$large_schema" '{name:"E2E Large Result Dashboard",description:"dashboard MVP ObjectRef result flow",schemaJson:$schema}')" | id_from)

jq -n \
  --arg jdbcDatasourceId "$jdbc_ds" --arg jdbcDatasetId "$jdbc_dataset" \
  --arg aloudataDatasourceId "$aloudata_ds" --arg aloudataDatasetId "$aloudata_dataset_id" \
  --arg httpDatasourceId "$http_ds" --arg httpDatasetId "$http_dataset" \
  --arg fileObjectId "$file_object_id" --arg fileDatasetId "$file_dataset" \
  --arg multiSourceDashboardId "$multi_dashboard" --arg apiFileDashboardId "$api_file_dashboard" \
  --arg echartsDashboardId "$echarts_dashboard" \
  --arg compatibilityDashboardId "$compat_dashboard" --arg errorDashboardId "$error_dashboard" \
  --arg cancelDashboardId "$cancel_dashboard" \
  --arg timeoutDashboardId "$timeout_dashboard" --arg resourceDashboardId "$resource_dashboard" \
  --arg largeDashboardId "$large_dashboard" \
  '{jdbcDatasourceId:$jdbcDatasourceId,jdbcDatasetId:$jdbcDatasetId,aloudataDatasourceId:$aloudataDatasourceId,aloudataDatasetId:$aloudataDatasetId,httpDatasourceId:$httpDatasourceId,httpDatasetId:$httpDatasetId,fileObjectId:$fileObjectId,fileDatasetId:$fileDatasetId,multiSourceDashboardId:$multiSourceDashboardId,apiFileDashboardId:$apiFileDashboardId,echartsDashboardId:$echartsDashboardId,compatibilityDashboardId:$compatibilityDashboardId,errorDashboardId:$errorDashboardId,cancelDashboardId:$cancelDashboardId,timeoutDashboardId:$timeoutDashboardId,resourceDashboardId:$resourceDashboardId,largeDashboardId:$largeDashboardId}' > "$STATE_FILE"

cat <<EOF
Seed completed. State: $STATE_FILE
$(MATECLAW_E2E_WORKSPACE_ID="$MATECLAW_E2E_WORKSPACE_ID" MATECLAW_E2E_STATE_FILE="$STATE_FILE" \
  "$(dirname "${BASH_SOURCE[0]}")/export-dashboard-mvp-env.sh")
EOF
