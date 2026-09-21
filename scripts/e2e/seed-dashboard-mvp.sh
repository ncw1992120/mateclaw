#!/usr/bin/env bash
set -euo pipefail

: "${MATECLAW_E2E_WORKSPACE_ID:?set MATECLAW_E2E_WORKSPACE_ID}"
aloudata_dataset_id="${MATECLAW_E2E_ALOUDATA_DATASET_ID:-}"
aloudata_mode="${MATECLAW_E2E_ALOUDATA_MODE:-}"

BASE_URL="${MATECLAW_E2E_API_BASE_URL:-http://127.0.0.1:18189/dataagent/api}"
FIXTURE="${MATECLAW_E2E_ORDERS_FIXTURE:-mateclaw-dataagent-ui/e2e/fixtures/orders.csv}"
STATE_FILE="${MATECLAW_E2E_STATE_FILE:-/tmp/mateclaw-dashboard-mvp-state-${MATECLAW_E2E_WORKSPACE_ID}.json}"
RUN_SUFFIX="${MATECLAW_E2E_RUN_SUFFIX:-$(date +%s)}"
JDBC_HOST="${MATECLAW_E2E_JDBC_HOST:-127.0.0.1}"
JDBC_PORT="${MATECLAW_E2E_JDBC_PORT:-13306}"
JDBC_DATABASE="${MATECLAW_E2E_JDBC_DATABASE:-mateclaw}"
JDBC_USERNAME="${MATECLAW_E2E_JDBC_USERNAME:-mateclaw}"
HTTP_ENDPOINT="${MATECLAW_E2E_HTTP_ENDPOINT:-https://e2e-http:8443/orders}"
HTTP_ALLOWED_HOST="${MATECLAW_E2E_HTTP_ALLOWED_HOST:-e2e-http}"
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
  --arg suffix "$RUN_SUFFIX" \
  --arg host "$JDBC_HOST" --argjson port "$JDBC_PORT" \
  --arg database "$JDBC_DATABASE" --arg username "$JDBC_USERNAME" \
  '{name:("E2E JDBC Orders " + $suffix),description:"dashboard MVP E2E",sourceType:"mysql",host:$host,port:$port,databaseName:$database,username:$username,password:$password,enabled:true,metaShared:true}')" | id_from)
jdbc_dataset=$(api POST /v1/datasets "$(jq -cn --arg ds "$jdbc_ds" --arg suffix "$RUN_SUFFIX" \
  '{name:("E2E JDBC Orders Dataset " + $suffix),description:"dashboard MVP JDBC source",sourceDefinition:{sourceType:"JDBC_SQL",datasourceId:$ds,sql:"SELECT id, customer_id, order_date, status, amount FROM orders WHERE id > 0"}}')" | id_from)

http_connection_params=$(jq -cn --arg endpoint "$HTTP_ENDPOINT" --arg allowedHost "$HTTP_ALLOWED_HOST" '{apiDefinitions:{orders:{endpoint:$endpoint,method:"GET",allowedHosts:[$allowedHost],allowedQueryParams:["status"],paginationMode:"none",resultPath:"$.data",schema:[
  {name:"id",title:"订单 ID",dataType:"INTEGER",role:"dimension"},
  {name:"order_date",title:"订单日期",dataType:"DATE",role:"dimension"},
  {name:"status",title:"状态",dataType:"STRING",role:"dimension"},
  {name:"amount",title:"金额",dataType:"DECIMAL",role:"measure"}
]}}}')
http_ds=$(api POST /v1/datasources "$(jq -cn \
  --arg connectionParams "$http_connection_params" \
  --arg suffix "$RUN_SUFFIX" \
  '{name:("E2E HTTP Orders " + $suffix),description:"dashboard MVP E2E",sourceType:"api",host:"e2e-http",port:8443,enabled:true,metaShared:true,connectionParams:$connectionParams}')" | id_from)
http_dataset=$(api POST /v1/datasets "$(jq -cn --arg ds "$http_ds" --arg suffix "$RUN_SUFFIX" \
  '{name:("E2E HTTP Orders Dataset " + $suffix),description:"dashboard MVP HTTP source",sourceDefinition:{sourceType:"HTTP_API",datasourceId:$ds,apiDefinitionId:"orders",schema:[
    {name:"id",title:"订单 ID",dataType:"INTEGER",role:"dimension"},
    {name:"order_date",title:"订单日期",dataType:"DATE",role:"dimension"},
    {name:"status",title:"状态",dataType:"STRING",role:"dimension"},
    {name:"amount",title:"金额",dataType:"DECIMAL",role:"measure"}
  ]}}')" | id_from)

file_ref=$(curl --fail-with-body --silent --show-error -X POST \
  -H "$AUTH_HEADER" -H "$WORKSPACE_HEADER" \
  -F "file=@${FIXTURE};type=text/csv" "$BASE_URL/v1/dataset-files" | jq -er '.data')
file_object_id=$(jq -er '.objectId' <<<"$file_ref")
file_dataset=$(api POST /v1/datasets "$(jq -cn --arg object "$file_object_id" --arg suffix "$RUN_SUFFIX" \
  '{name:("E2E File Orders Dataset " + $suffix),description:"dashboard MVP file source",sourceDefinition:{sourceType:"FILE",objectId:$object,format:"CSV",schemaVersion:1}}')" | id_from)

dashboard_schema() {
  local left_id="$1" left_name="$2" right_id="$3" right_name="$4" script="$5"
  jq -cn --arg l "$left_id" --arg ln "$left_name" --arg r "$right_id" --arg rn "$right_name" --arg script "$script" '{
    version:"1.1",
    pages:[{id:"e2e-page",name:"E2E",components:[{id:"e2e-table",type:"table",title:"E2E Result",position:{x:0,y:0,w:12,h:6},renderType:"table",dataSource:{datasourceId:"",metrics:[],dimensions:[],filters:[],limit:100},config:{datasetPipeline:{datasetInputs:[{datasetId:$l,inputName:$ln},{datasetId:$r,inputName:$rn}],script:$script,parameters:[],scriptFilterBindings:[]}}}]}],
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
    pages:[{id:"e2e-chart-page",name:"E2E Chart",components:[{id:"e2e-chart",type:"chart",title:"E2E Script Chart",position:{x:0,y:0,w:12,h:6},renderType:"echarts",dataSource:{datasourceId:"",metrics:[],dimensions:[],filters:[],limit:100},config:{datasetPipeline:{datasetInputs:[{datasetId:$dataset,inputName:"jdbc_orders"}],script:$script,parameters:[],scriptFilterBindings:[]}}}]}],
    datasetInputs:[{datasetId:$dataset,inputName:"jdbc_orders"}],
    script:$script,
    parameters:[],
    executionPolicy:{timeoutSeconds:60,maxOutputBytes:50000},
    scriptBindings:[{componentId:"e2e-chart",renderType:"echarts"}]
  }'
}

python_dashboard_schema() {
  local dataset_id="$1" input_name="$2" script="$3" system_mode="$4" system_code="$5" user_code="$6" right_id="${7:-}" right_input="${8:-}"
  jq -cn --arg dataset "$dataset_id" --arg input "$input_name" --arg script "$script" \
    --arg mode "$system_mode" --arg system "$system_code" --arg user "$user_code" --arg right "$right_id" --arg rightInput "$right_input" ' {
    version:"1.1",
    pages:[{id:"python-page",name:"Python E2E",components:[{id:"python-table",type:"table",title:"Python E2E Result",position:{x:0,y:0,w:12,h:6},renderType:"table",dataSource:{datasourceId:"",metrics:[],dimensions:[],filters:[],limit:100},config:{datasetPipeline:{datasetInputs:([ {datasetId:$dataset,inputName:$input} ] + (if $right != "" then [{datasetId:$right,inputName:$rightInput}] else [] end)),script:$script,parameters:[],scriptFilterBindings:[],systemScript:{mode:$mode,generatedCode:$system,managedCode:(if $mode == "managed" then $system else null end),generatedFingerprint:"seed",userCode:$user}}}}]}],
    datasetInputs:([ {datasetId:$dataset,inputName:$input} ] + (if $right != "" then [{datasetId:$right,inputName:$rightInput}] else [] end)),script:$script,parameters:[],executionPolicy:{timeoutSeconds:60,maxOutputBytes:50000},scriptBindings:[{componentId:"python-table",renderType:"table"}]
  }'
}

jdbc_aloudata_script=$'left = datasets.read(input_name="jdbc_orders")\nright = datasets.read(input_name="aloudata_metrics", filters=[{"field": "region", "operator": "eq", "value": "east"}])\nresult = left.to_polars().to_dicts() + right.to_polars().to_dicts()'
api_file_script=$'paid_filter = {"field": "status", "operator": "eq", "value": "PAID"}\napi_rows = datasets.read(input_name="api_orders", filters=[paid_filter])\nfile_rows = datasets.read(input_name="file_orders", filters=[paid_filter])\nresult = api_rows.to_polars().to_dicts() + file_rows.to_polars().to_dicts()'

multi_dashboard=""
aloudata_ds=""
if [[ "$aloudata_mode" == "simulation" ]]; then
  # 本机模拟服务通过宿主机端口暴露；使用 HTTP 避免 WireMock 自签名 TLS 在 JVM
  # 中的证书握手问题。生产环境仍应填写真实的 HTTPS 产品层/语义层地址。
  aloudata_connection_params=$(jq -cn --arg host "${MATECLAW_E2E_ALOUDATA_HOST:-127.0.0.1}" --argjson port "${MATECLAW_E2E_ALOUDATA_PORT:-18081}" '{anymetricsHost:("http://" + $host),semanticHost:("http://" + $host),anymetricsPort:$port,semanticPort:$port,authType:"UID"}')
  aloudata_ds=$(api POST /v1/datasources "$(jq -cn \
    --arg connectionParams "$aloudata_connection_params" \
    --arg password "$(encrypt_field local-simulation-auth)" \
    --arg suffix "$RUN_SUFFIX" \
    --arg host "${MATECLAW_E2E_ALOUDATA_HOST:-127.0.0.1}" --argjson port "${MATECLAW_E2E_ALOUDATA_PORT:-18081}" \
    '{name:("E2E Aloudata Simulation " + $suffix),description:"dashboard MVP simulated Aloudata",sourceType:"aloudata",host:$host,port:$port,productHost:("http://" + $host),semanticHost:("http://" + $host),username:"local-tenant",password:$password,enabled:true,metaShared:true,connectionParams:$connectionParams}')" | id_from)
  # 创建后显式触发同步，并等待指标/维度元数据可读。同步还会尝试生成向量，
  # 本地环境可能没有外部 embedding 服务，因此只等待页面所需的分页数据，不等待
  # 后置索引任务；短超时断开客户端不会取消服务端已启动的同步。
  curl --max-time 3 --silent --show-error -X POST \
    -H "$AUTH_HEADER" -H "$WORKSPACE_HEADER" "$BASE_URL/v1/datasources/${aloudata_ds}/aloudata/sync" >/dev/null || true
  metadata_ready=false
  for attempt in $(seq 1 30); do
    metric_count=$(api GET "/v1/datasources/${aloudata_ds}/aloudata/synced-metrics?pageNumber=1&pageSize=1" | jq '.data | length')
    dimension_count=$(api GET "/v1/datasources/${aloudata_ds}/aloudata/synced-dimensions?pageNumber=1&pageSize=1" | jq '.data | length')
    if [[ "$metric_count" -gt 0 && "$dimension_count" -gt 0 ]]; then
      metadata_ready=true
      break
    fi
    sleep 1
  done
  [[ "$metadata_ready" == true ]] || {
    echo "Timed out waiting for Aloudata simulation metric/dimension metadata" >&2
    exit 1
  }
  aloudata_dataset_id=$(api POST /v1/datasets "$(jq -cn --arg ds "$aloudata_ds" --arg suffix "$RUN_SUFFIX" \
    '{name:("E2E Aloudata Metrics Dataset " + $suffix),description:"dashboard MVP simulated metric view",sourceDefinition:{sourceType:"ALOUDATA_ANALYSIS_VIEW",datasourceId:$ds,analysisViewId:"local_sales_view"}}')" | id_from)
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
echarts_script=$'rows = datasets.read(input_name="jdbc_orders", filters=[{"field": "status", "operator": "eq", "value": "PAID"}])\nresult = rows.to_polars().select(["status", "amount"]).to_dicts()'
echarts_schema=$(echarts_dashboard_schema "$jdbc_dataset" "$echarts_script")
echarts_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$echarts_schema" '{name:"E2E ECharts Binding Dashboard",description:"dashboard MVP ECharts script binding flow",schemaJson:$schema}')" | id_from)
compat_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema '{"version":"1.0","components":[]}' '{name:"E2E Legacy Compatibility Dashboard",description:"dashboard MVP legacy schema",schemaJson:$schema}')" | id_from)
error_schema=$(jq -cn --arg dataset "$http_dataset" --arg script 'raise RuntimeError("e2e expected script failure")' '{version:"1.1",pages:[{id:"e2e-error-page",name:"E2E Error",components:[{id:"e2e-error-table",type:"table",title:"E2E Error",position:{x:0,y:0,w:12,h:6},renderType:"table",config:{datasetPipeline:{datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:$script,parameters:[],scriptFilterBindings:[]}}}]}],datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:$script,parameters:[],executionPolicy:{timeoutSeconds:60,maxOutputBytes:50000}}')
error_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$error_schema" '{name:"E2E Script Error Dashboard",description:"dashboard MVP controlled script failure",schemaJson:$schema}')" | id_from)
cancel_schema=$(jq -cn --arg dataset "$http_dataset" --arg script $'import time\ntime.sleep(30)\nresult = []' '{version:"1.1",pages:[{id:"e2e-cancel-page",name:"E2E Cancel",components:[{id:"e2e-cancel-table",type:"table",title:"E2E Cancel",position:{x:0,y:0,w:12,h:6},renderType:"table",config:{datasetPipeline:{datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:$script,parameters:[],scriptFilterBindings:[]}}}]}],datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:$script,parameters:[],executionPolicy:{timeoutSeconds:60,maxOutputBytes:50000}}')
cancel_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$cancel_schema" '{name:"E2E Cancellation Dashboard",description:"dashboard MVP controlled long-running script",schemaJson:$schema}')" | id_from)
timeout_schema=$(jq -cn --arg dataset "$http_dataset" --arg script $'import time\ntime.sleep(5)\nresult = []' '{version:"1.1",pages:[{id:"e2e-timeout-page",name:"E2E Timeout",components:[{id:"e2e-timeout-table",type:"table",title:"E2E Timeout",position:{x:0,y:0,w:12,h:6},renderType:"table",config:{datasetPipeline:{datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:$script,parameters:[],scriptFilterBindings:[]}}}]}],datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:$script,parameters:[],executionPolicy:{timeoutSeconds:1,maxOutputBytes:50000}}')
timeout_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$timeout_schema" '{name:"E2E Script Timeout Dashboard",description:"dashboard MVP controlled script timeout",schemaJson:$schema}')" | id_from)
resource_schema=$(jq -cn --arg dataset "$http_dataset" --arg script $'print("x" * 60000)\nresult = []' '{version:"1.1",pages:[{id:"e2e-resource-page",name:"E2E Resource",components:[{id:"e2e-resource-table",type:"table",title:"E2E Resource",position:{x:0,y:0,w:12,h:6},renderType:"table",config:{datasetPipeline:{datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:$script,parameters:[],scriptFilterBindings:[]}}}]}],datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:$script,parameters:[],executionPolicy:{timeoutSeconds:60,maxOutputBytes:50000}}')
resource_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$resource_schema" '{name:"E2E Resource Limit Dashboard",description:"dashboard MVP controlled stdout limit",schemaJson:$schema}')" | id_from)
large_schema=$(jq -cn --arg dataset "$http_dataset" --arg script 'result = [{"id": i, "payload": "x" * 2000} for i in range(1000)]' '{version:"1.1",pages:[{id:"e2e-large-page",name:"E2E Large Result",components:[{id:"e2e-large-table",type:"table",title:"E2E Large Result",position:{x:0,y:0,w:12,h:6},renderType:"table",config:{datasetPipeline:{datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:$script,parameters:[],scriptFilterBindings:[]}}}]}],datasetInputs:[{datasetId:$dataset,inputName:"api_orders"}],script:$script,parameters:[],executionPolicy:{timeoutSeconds:60,maxOutputBytes:50000}}')
large_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$large_schema" '{name:"E2E Large Result Dashboard",description:"dashboard MVP ObjectRef result flow",schemaJson:$schema}')" | id_from)

# Python 编排专项种子：名称和 ID 均写入状态文件，E2E 不硬编码资源 ID。
python_filter_script=$'rows = datasets.read(input_name="python_orders")\nresult = rows.to_polars().to_dicts()'
python_filter_schema=$(python_dashboard_schema "$http_dataset" python_orders "$python_filter_script" generated 'python_orders = datasets.read(input_name="python_orders", filters=[])' 'result = python_orders.to_polars().to_dicts()')
python_filter_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$python_filter_schema" '{name:"Python Filter Dashboard",description:"Python runtime filter template dashboard",schemaJson:$schema}')" | id_from)

python_ab_script=$'left = datasets.read(input_name="dataset_a")\nkeys = left.to_polars().select(["id"]).to_dicts()\nright = datasets.read(input_name="dataset_b", filters=[{"field":"id","operator":"in","value":[row["id"] for row in keys]}])\nresult = right.to_polars().to_dicts()'
python_ab_schema=$(python_dashboard_schema "$http_dataset" dataset_a "$python_ab_script" generated 'dataset_a = datasets.read(input_name="dataset_a", filters=[])' 'result = dataset_a.to_polars().to_dicts()' "$file_dataset" dataset_b)
python_ab_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$python_ab_schema" '{name:"Python A-to-B Dashboard",description:"Python dataset A to B filtering dashboard",schemaJson:$schema}')" | id_from)

python_managed_schema=$(python_dashboard_schema "$http_dataset" managed_orders 'managed_orders = datasets.read(input_name="managed_orders")\nresult = managed_orders.to_polars().to_dicts()' managed 'managed_orders = custom_read()' 'result = managed_orders')
python_managed_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$python_managed_schema" '{name:"Python Managed System Dashboard",description:"Python managed system region dashboard",schemaJson:$schema}')" | id_from)

python_output_schema=$(python_dashboard_schema "$http_dataset" output_orders 'result = {"schemaVersion":"1.0","kind":"table","data":{"columns":[{"name":"status","title":"状态","dataType":"string","nullable":true}],"rows":[{"status":"PAID"}]},"meta":{"rowCount":1,"truncated":false,"sourceInputs":["output_orders"]}}' generated 'output_orders = datasets.read(input_name="output_orders")' 'result = output_orders.to_polars().to_dicts()')
python_output_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$python_output_schema" '{name:"Python Output Contract Dashboard",description:"Python output envelope dashboard",schemaJson:$schema}')" | id_from)

python_output_error_schema=$(python_dashboard_schema "$http_dataset" error_orders 'result = {"schemaVersion":"9.9","kind":"unknown","data":{},"meta":{}}' generated 'error_orders = datasets.read(input_name="error_orders")' 'result = error_orders.to_polars().to_dicts()')
python_output_error_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$python_output_error_schema" '{name:"Python Output Error Dashboard",description:"Python output contract error dashboard",schemaJson:$schema}')" | id_from)

python_large_dashboard=$(api POST /v1/insight/dashboards "$(jq -cn --arg schema "$large_schema" '{name:"Python Large Result Dashboard",description:"Python large result dashboard",schemaJson:$schema}')" | id_from)

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
  --arg pythonFilterDashboardId "$python_filter_dashboard" --arg pythonAToBDashboardId "$python_ab_dashboard" \
  --arg pythonManagedDashboardId "$python_managed_dashboard" --arg pythonOutputDashboardId "$python_output_dashboard" \
  --arg pythonOutputErrorDashboardId "$python_output_error_dashboard" --arg pythonLargeDashboardId "$python_large_dashboard" \
  '{jdbcDatasourceId:$jdbcDatasourceId,jdbcDatasetId:$jdbcDatasetId,aloudataDatasourceId:$aloudataDatasourceId,aloudataDatasetId:$aloudataDatasetId,httpDatasourceId:$httpDatasourceId,httpDatasetId:$httpDatasetId,fileObjectId:$fileObjectId,fileDatasetId:$fileDatasetId,multiSourceDashboardId:$multiSourceDashboardId,apiFileDashboardId:$apiFileDashboardId,echartsDashboardId:$echartsDashboardId,compatibilityDashboardId:$compatibilityDashboardId,errorDashboardId:$errorDashboardId,cancelDashboardId:$cancelDashboardId,timeoutDashboardId:$timeoutDashboardId,resourceDashboardId:$resourceDashboardId,largeDashboardId:$largeDashboardId,pythonFilterDashboardId:$pythonFilterDashboardId,pythonAToBDashboardId:$pythonAToBDashboardId,pythonManagedDashboardId:$pythonManagedDashboardId,pythonOutputDashboardId:$pythonOutputDashboardId,pythonOutputErrorDashboardId:$pythonOutputErrorDashboardId,pythonLargeDashboardId:$pythonLargeDashboardId}' > "$STATE_FILE"

cat <<EOF
Seed completed. State: $STATE_FILE
$(MATECLAW_E2E_WORKSPACE_ID="$MATECLAW_E2E_WORKSPACE_ID" MATECLAW_E2E_STATE_FILE="$STATE_FILE" \
  "$(dirname "${BASH_SOURCE[0]}")/export-dashboard-mvp-env.sh")
EOF
