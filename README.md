# Jenkins Shared Library（Fortify 掃描）

## 專案概觀

此 repository 維護 Jenkins Fortify 掃描流程使用的 Shared Library、總管 Pipeline、子專案 Pipeline 與 Fortify shell。

```text
專案根目錄
├─ fortify/
│  ├─ Jenkinsfile .............. Fortify 掃描總管流程，依參數觸發各產品掃描 Job
│  ├─ Jenkinsfile-release ...... 掃描前 release/publishToMavenLocal 總管流程
│  ├─ <project>/Jenkinsfile .... 子專案掃描流程，指定 Git 儲存庫與 shell
│  └─ shells/*.sh .............. Fortify wrapper scripts，掃描前會複製到 Jenkins 主機
├─ vars/
│  └─ fortifyScan.groovy ....... Pipeline 共用函式，內含 Apply Parameters、Clone、Scan、Archive Outputs 階段
└─ README.md ................... 使用說明與檔案對照
```

## Pipeline 一覽

| Jenkinsfile | Jenkins Job | Git 儲存庫 | Shell Script | 功能摘要 |
| --- | --- | --- | --- | --- |
| `fortify/Jenkinsfile` | 掃描總管 Job | 依參數觸發下游 Job | - | 建立掃描參數，依設定選擇是否先觸發 `FORTIFY-RELEASE-FSAP-FLOW-UTILS`，再呼叫各 `FORTIFY-*` 掃描 Job，最後打包並歸檔 artifacts 與 reports。 |
| `fortify/Jenkinsfile-release` | Release 總管 Job | 依序觸發 release 下游 Job | - | 觸發多個 `FORTIFY-RELEASE-*` Job，主要用於掃描前將共用模組 publish 到 Maven local。 |
| `fortify/fsap-adm/Jenkinsfile` | `FORTIFY-FSAP-ADM` | `ssh://git@bt-gitea:22/BOT/fsap-adm.git` | `Fortify-FSAP-ADM.sh` | 掃描 FSAP-ADM 多個 Gradle 子模組。 |
| `fortify/fsap-runtime/Jenkinsfile` | `FORTIFY-FSAP-RUNTIME` | `ssh://git@bt-gitea:22/BOT/fsap-runtime.git` | `Fortify-FSAP-RUNTIME.sh` | 掃描 FSAP-RUNTIME 的 `boot:runtime`。 |
| `fortify/fsap-gateway/Jenkinsfile` | `FORTIFY-FSAP-GATEWAY` | `ssh://git@bt-gitea:22/BOT/fsap-gateway.git` | `Fortify-FSAP-GATEWAY.sh` | 掃描 FSAP-GATEWAY 的 `boot:fastgateway`。 |
| `fortify/fsap-model/Jenkinsfile` | `FORTIFY-FSAP-MODEL` | `ssh://git@bt-gitea:22/BOT/fsap-model.git` | `Fortify-FSAP-MODEL.sh` | 掃描 FSAP-MODEL 的 `model-grpc-common`。 |
| `fortify/fsap-bpmn-utils/Jenkinsfile` | `FORTIFY-FSAP-BPMN-UTILS` | `ssh://git@bt-gitea:22/BOT/bpmn-utils.git` | `Fortify-FSAP-BPMN-UTILS.sh` | 掃描 BPMN 工具套件的 `bpmn-utils`。 |
| `fortify/fsap-month-report/Jenkinsfile` | `FORTIFY-FSAP-MONTH-REPORT` | `ssh://git@bt-gitea:22/UTILS/fsap-month-report.git` | `Fortify-FSAP-MONTH-REPORT.sh` | 掃描 FSAP-MONTH-REPORT 專案，執行 `clean bootJar`。 |
| `fortify/tx-control/Jenkinsfile` | `FORTIFY-TXCONTROL` | `ssh://git@bt-gitea:22/BOT/tx-control.git` | `Fortify-TXCONTROL.sh` | 掃描 TXCONTROL 專案。 |
| `fortify/fac/Jenkinsfile` | `FORTIFY-FAC` | `ssh://git@bt-gitea:22/BOT/fac.git` | `Fortify-FAC.sh` | 掃描 FAC 專案。 |
| `fortify/ncb/Jenkinsfile` | `FORTIFY-NCB` | `ssh://git@bt-gitea:22/BOT/ncb.git` | `Fortify-NCB.sh` | 掃描 NCB 專案。 |
| `fortify/ncl/Jenkinsfile` | `FORTIFY-NCL` | `ssh://git@bt-gitea:22/BOT/ncl.git` | `Fortify-NCL.sh` | 掃描 NCL 專案。 |
| `fortify/ncl-batch/Jenkinsfile` | `FORTIFY-NCL-BATCH` | `ssh://git@bt-gitea:22/BOT/ncl-batch.git` | `Fortify-NCL-BATCH.sh` | 掃描 NCL 批次專案。 |

## 掃描總管：`fortify/Jenkinsfile`

總管 Job 使用 `agent any`，並套用：

- `timestamps()`
- `ansiColor('xterm')`
- `timeout(time: 6, unit: 'HOURS')`

第一次執行時會建立下列參數，之後需使用 `Build with Parameters` 觸發實際流程。

| 參數 | 類型 | 說明 |
| --- | --- | --- |
| `git_branch_name` | choice | 目標 Git branch，選項為 `sit`、`master`。選擇 `master` 時，總管會將掃描環境設定為 `uat`；選擇 `sit` 時，掃描環境為 `sit`。 |
| `is_release_utils` | boolean | 是否先觸發 `FORTIFY-RELEASE-FSAP-FLOW-UTILS`，預設為 `true`。 |
| `is_scan_fsap_adm` | boolean | 是否觸發 `FORTIFY-FSAP-ADM`。 |
| `is_scan_fsap_runtime` | boolean | 是否觸發 `FORTIFY-FSAP-RUNTIME`。 |
| `is_scan_fsap_gateway` | boolean | 是否觸發 `FORTIFY-FSAP-GATEWAY`。 |
| `is_scan_fsap_model` | boolean | 是否觸發 `FORTIFY-FSAP-MODEL`。 |
| `is_scan_fsap_bpmn_utils` | boolean | 是否觸發 `FORTIFY-FSAP-BPMN-UTILS`。 |
| `is_scan_fsap_month_report` | boolean | 是否觸發 `FORTIFY-FSAP-MONTH-REPORT`。 |
| `is_scan_txcontrol` | boolean | 是否觸發 `FORTIFY-TXCONTROL`。 |
| `is_scan_ncl` | boolean | 是否觸發 `FORTIFY-NCL`。 |
| `is_scan_ncl_batch` | boolean | 是否觸發 `FORTIFY-NCL-BATCH`。 |
| `is_scan_fac` | boolean | 是否觸發 `FORTIFY-FAC`。 |
| `is_scan_ncb` | boolean | 是否觸發 `FORTIFY-NCB`。 |

執行順序：

1. `RELEASE-UTILS`：只有 `is_release_utils=true` 時才呼叫 `FORTIFY-RELEASE-FSAP-FLOW-UTILS`，並傳入 `git_branch_name` 與對應的 `env`。
2. 依參數勾選狀態依序觸發：`FSAP-MODEL`、`FSAP-BPMN-UTILS`、`FSAP-RUNTIME`、`FSAP-ADM`、`FSAP-GATEWAY`、`TXCONTROL`、`NCL`、`NCL-BATCH`、`FAC`、`NCB`、`FSAP-MONTH-REPORT`。每個下游 Job 都會收到 `git_branch_name` 與 `env`。
3. 每個產品 stage 記錄本次下游 Job 的 Build 編號與結果；只有成功且產物完整的 Build，才以 Copy Artifact 的指定 Build 編號選取方式取得歸檔。
4. `artifacts`：只將本次成功收集的專案分別打包成 `artifacts.tar.gz`、`reports.tar.gz`，再以 `archiveArtifacts` 歸檔；不搜尋其他 Job 的 workspace，也不使用最後成功的歷史 Build。

單一產品掃描失敗、缺少預期產物或複製歸檔失敗時，後續專案仍繼續執行，總管標示為 `UNSTABLE`，該專案不納入兩個壓縮檔。全部失敗時不產生空壓縮檔；未勾選任何專案時不產生壓縮檔。當 Release 總管回報失敗時，也會使掃描總管標示為 `UNSTABLE`，但不阻止後續掃描。使用者取消或總管 timeout 則中止流程。

子 Job 的產物契約：

- `scan-output/reports/`：OWASP Top 10 與 Developer Workbook 的 PDF、HTML，共四份報告，檔案均須存在且非空。
- `scan-output/artifacts/`：本次 checkout 與建置後，各模組 `build/libs/` 下的 jar/war，至少一份且均非空；保留相對模組路徑，不收集 wrapper 或 `libs/` 下的依賴 jar。
- 子 Job 在驗證成功後才歸檔 `scan-output/**`；Report Generator 失敗或產物驗證失敗時，子 Job 失敗，總管不使用其產物。

總管保留每個專案的目錄，以避免同名檔案互相覆蓋，例如只勾選 NCB、FAC 且都成功時：

```text
reports.tar.gz
  FORTIFY-NCB/NCB_OWASPTop10.pdf
  FORTIFY-NCB/NCB_OWASPTop10.html
  FORTIFY-NCB/NCB_DeveloperWorkbook.pdf
  FORTIFY-NCB/NCB_DeveloperWorkbook.html
  FORTIFY-FAC/...
artifacts.tar.gz
  FORTIFY-NCB/<module>/build/libs/ncb-server.jar
  FORTIFY-FAC/...
```

## Release 總管：`fortify/Jenkinsfile-release`

Release 總管 Job 使用 `timeout(time: 1, unit: 'HOURS')`，第一次執行時會建立：

| 參數 | 類型 | 說明 |
| --- | --- | --- |
| `git_branch_name` | choice | 目標 Git branch，選項為 `sit`、`master`。 |
| `env` | choice | 發佈環境，選項為 `sit`、`uat`。 |
| `is_offline` | boolean | 是否以離線包版方式執行，預設為 `true`。 |

有參數觸發後會依序呼叫下列下游 Job：

| Stage | 下游 Job |
| --- | --- |
| `BPMN-UTILS` | `FORTIFY-RELEASE-FSAP-BPMN-UTILS` |
| `MODEL` | `FORTIFY-RELEASE-FSAP-MODEL` |
| `LOG-AGENT-COMMON` | `FORTIFY-RELEASE-FSAP-LOG-AGENT-COMMON` |
| `ACTURATOR-SECURITY` | `FORTIFY-RELEASE-FSAP-ACTURATOR-SECURITY` |
| `DISPATCHER-CLIENT` | `FORTIFY-RELEASE-FSAP-DISPATCHER-CLIENT` |
| `MONITOR-PLUGIN` | `FORTIFY-RELEASE-FSAP-MONITOR-PLUGIN` |
| `LOG-AGENT-CLIENT` | `FORTIFY-RELEASE-FSAP-LOG-AGENT-CLIENT` |
| `COMMAND-UTILS` | `FORTIFY-RELEASE-FSAP-COMMAND-UTILS` |
| `RUNTIME` | `FORTIFY-RELEASE-FSAP-RUNTIME` |
| `TX-CONTROL` | `FORTIFY-RELEASE-TX-CONTROL` |
| `NCL` | `FORTIFY-RELEASE-NCL` |

多數下游 Job 會帶入固定參數：

- `git_branch_name=params.git_branch_name`
- `env=params.env`
- `is_assemble=true`
- `is_test_report=false`
- `is_code_analysis=false`
- `is_owasp_analysis=false`
- `is_sonar_analysis=false`
- `is_put_files=false`
- `is_remote_start=false`
- `is_offline=params.is_offline`
- `only_publishToMavenLocal=true`

`RUNTIME` stage 目前沒有傳入 `only_publishToMavenLocal`。

## 共用函式：`vars/fortifyScan.groovy`

所有子專案掃描 Jenkinsfile 都透過 shared library 載入共用函式：

```groovy
@Library('fortifyScan') _
fortifyScan(
    gitUrl: 'ssh://git@bt-gitea:22/BOT/<project>.git',
    shellName: 'Fortify-<PROJECT>.sh'
)
```

主要 stage：

1. **Apply Parameters**
   - 若 Jenkins 已帶入任何參數，設定 `env.is_param_from_UI = 'true'`。
   - 若沒有參數，設定 `env.is_param_from_UI = 'false'`，並建立 `git_branch_name`、`env`、`GIT_URL`、`SHELL_NAME`、`JENKINS_JDK_TOOL` 參數。
   - `JENKINS_JDK_TOOL` 會讀取 Jenkins Global Tool Configuration 中已註冊的 JDK；未指定時使用第一組 JDK。

2. **Clone**
   - `cleanWs()` 清空 workspace。
   - 從 `ssh://git@bt-gitea:22/BOT/jenkins-shared-library.git` 的 `main` branch 進行 Sparse Checkout，只取 `fortify/shells`。
   - 將 `fortify/shells/*.sh` 複製到 `~/Fortify/shells/`。
   - 再次 `cleanWs()`。
   - 使用 Jenkins credential `gitea` checkout `params.GIT_URL`，branch 為 `params.git_branch_name`。

3. **Scan**
   - Fortify shell 路徑固定為 `/var/jenkins_home/Fortify/shells/${params.SHELL_NAME}`。
   - 執行 `sudo chown 1111:1111` 與 `sudo chmod +x` 設定 shell 權限。
   - 執行 `sudo chmod +x gradlew`。
   - 執行 `${fortifyScript} ${params.env}`，將 `sit` 或 `uat` 傳入 shell 第一個參數；`git_branch_name` 與 `env` 可不同，例如 `master` branch 對應 `uat` 環境。
   - 先清空本次 `scan-output/`，再以 `FORTIFY_REPORT_DIR` 指定 workspace 下的 `scan-output/reports/`，讓 Shell 將四份報告直接寫入本次 Build 的目錄。

4. **Archive Outputs**
   - 執行 `Collect-Fortify-Outputs.sh`，驗證四份報告並收集各模組 `build/libs/` 下的 jar/war。
   - 驗證成功後歸檔 `scan-output/**`，讓總管依確切 Build 編號取得產物；直接執行子 Job 也可以從該 Build 下載。

## Fortify Shell 一覽

各 `Fortify-*.sh` 掃描 Shell 都會接收第一個參數作為 `ENV`，並使用 `BUILDID` 與時間戳建立報表目錄；`Collect-Fortify-Outputs.sh` 僅收集目前 workspace 的產物，不接收環境參數：

```text
REPORT_PATH=/var/jenkins_home/Fortify/reports/<BUILDID>/<ENV>/<yyyyMMdd_HHmmss>
FPR=$REPORT_PATH/<BUILDID>.fpr
```

Shell 內固定加入 Fortify 26.2.0 工具路徑：

```text
/var/jenkins_home/Fortify/OpenText_SAST_Fortify_26.2.0/bin
/var/jenkins_home/Fortify/OpenText_Application_Security_Tools_26.2.0/bin
```

掃描完成後，shell 會透過 `BIRTReportGenerator` 在同一個 `REPORT_PATH` 下產出下列報表：

| 報表 | 格式 | 輸出檔名 |
| --- | --- | --- |
| OWASP Top 10 | PDF | `<BUILDID>_OWASPTop10.pdf` |
| OWASP Top 10 | HTML | `<BUILDID>_OWASPTop10.html` |
| Developer Workbook | PDF | `<BUILDID>_DeveloperWorkbook.pdf` |
| Developer Workbook | HTML | `<BUILDID>_DeveloperWorkbook.html` |

Jenkins 執行時，報告先寫入 `FORTIFY_REPORT_DIR`，再複製到歷史 `REPORT_PATH`；手動執行且未設定此變數時，報告先寫入目前目錄。任一報告產生或複製失敗，都以非零狀態結束。

OWASP Top 10 報表會帶入 `--SecurityIssueDetails`，包含弱點明細。

| Shell | BUILDID | 預期 workspace | Gradle build 指令摘要 |
| --- | --- | --- | --- |
| `Fortify-FSAP-ADM.sh` | `FSAP-ADMIN` | `/var/jenkins_home/workspace/FORTIFY-FSAP-ADM` | 依序 build `fsap-admin-api`、`fsap-batch`、`fsap-common-service`、`log-agent:fsap-log-agent-server`、`fsap-schedule`、`fsap-monitor:fsap-service-monitor`，皆加上 `-x check --offline`。 |
| `Fortify-FSAP-RUNTIME.sh` | `FSAP-RUNTIME` | `/var/jenkins_home/workspace/FORTIFY-FSAP-RUNTIME` | `boot:runtime:clean boot:runtime:build -x publishToMavenLocal -x check --offline`。 |
| `Fortify-FSAP-GATEWAY.sh` | `FSAP-GATEWAY` | `/var/jenkins_home/workspace/FORTIFY-FSAP-GATEWAY` | `boot:fastgateway:clean boot:fastgateway:build -x check --offline`。 |
| `Fortify-FSAP-MODEL.sh` | `FSAP-MODEL` | `/var/jenkins_home/workspace/FORTIFY-FSAP-MODEL` | `model-grpc-common:clean model-grpc-common:build -x publishToMavenLocal -x check --offline`。 |
| `Fortify-FSAP-BPMN-UTILS.sh` | `FSAP-BPMN-UTILS` | `/var/jenkins_home/workspace/FORTIFY-FSAP-BPMN-UTILS` | `bpmn-utils:clean bpmn-utils:build -x publishToMavenLocal -x check --offline`。 |
| `Fortify-FSAP-MONTH-REPORT.sh` | `FSAP-MONTH-REPORT` | `/var/jenkins_home/workspace/FORTIFY-FSAP-MONTH-REPORT` | `clean bootJar --offline`。 |
| `Fortify-TXCONTROL.sh` | `TXCONTROL` | `/var/jenkins_home/workspace/FORTIFY-TXCONTROL` | `clean build -x publishToMavenLocal -x check --offline`。 |
| `Fortify-FAC.sh` | `FAC` | `/var/jenkins_home/workspace/FORTIFY-FAC` | `clean build -x check --offline`。 |
| `Fortify-NCB.sh` | `NCB` | `/var/jenkins_home/workspace/FORTIFY-NCB` | `clean build -x check --offline`。 |
| `Fortify-NCL.sh` | `NCL` | `/var/jenkins_home/workspace/FORTIFY-NCL` | `clean build -x check --offline`。 |
| `Fortify-NCL-BATCH.sh` | `NCL-BATCH` | `/var/jenkins_home/workspace/FORTIFY-NCL-BATCH` | `clean build -x check --offline`。 |

## 流程圖

```mermaid
flowchart LR
  A([使用者或排程觸發掃描總管 Job]) --> B[Apply Parameters]
  B --> C{is_release_utils?}
  C -->|是| D[觸發 FORTIFY-RELEASE-FSAP-FLOW-UTILS]
  C -->|否| E[依布林參數觸發各 FORTIFY 掃描 Job]
  D --> E
  E --> F[子 Job 載入 fortifyScan]
  F --> G[Sparse Checkout 取得 fortify/shells]
  G --> H[複製 shell 到 Jenkins Agent]
  H --> I[checkout 目標專案 git_branch_name]
  I --> J[依 branch 設定 env：sit 或 uat]
  J --> K[執行 Fortify shell]
  K --> L[sourceanalyzer clean/build/translate/scan]
  L --> M[BIRTReportGenerator 產出 PDF/HTML 報表]
  M --> N[子 Job 驗證並歸檔本次產物]
  N --> O[總管依指定 Build 編號收集成功專案]
  O --> P[打包 artifacts.tar.gz 與 reports.tar.gz]
```

## Jenkins 環境需求

- Jenkins Global Pipeline Libraries 需註冊 shared library 名稱 `fortifyScan`，並指向此 repository。
- Jenkins credential `gitea` 需可讀取此 repository 與所有目標專案 repository。
- Jenkins Global Tool Configuration 需至少註冊一組 JDK，供 `JENKINS_JDK_TOOL` 使用。
- Jenkins Agent 需安裝 Fortify SCA 與 Application Security Tools 26.2.0，且路徑需符合 shell 內設定。
- Jenkins Agent 需可執行 `BIRTReportGenerator`，用於產出 OWASP Top 10 與 Developer Workbook 報表。
- Jenkins Agent 需可執行 `sudo chown`、`sudo chmod`，以調整 `/var/jenkins_home/Fortify/shells/*.sh` 與 `gradlew` 權限。
- Jenkins Agent 需具備 Pipeline 中使用的 step/plugin，例如 `cleanWs`、`timestamps`、`ansiColor`、`archiveArtifacts`。
- Jenkins 需安裝 [Copy Artifact plugin](https://plugins.jenkins.io/copyartifact)，並在各子 Job 的 Copy Artifact 權限設定中允許 `FORTIFY-ALL-SCAN-FLOW` 讀取其歸檔；若 Job 位於資料夾，需使用正確的完整 Job 名稱。總管與子 Job 必須部署相同的產物契約版本；舊子 Job 未歸檔 `scan-output/**` 時會被視為收集失敗。
- 各目標專案需存在 `sit` 與 `master` branch；總管流程會將 `sit` 對應到 `sit` 環境、`master` 對應到 `uat` 環境。
- Shell 內的 `PROJECTROOT0` 是固定 workspace 路徑，Jenkins Job 名稱或 workspace 設定需與該路徑一致。
- Shell 使用 Gradle `--offline`，因此 Jenkins Maven/Gradle cache 需事先備妥依賴。

## 使用方式

1. 在 Jenkins Global Pipeline Libraries 新增名稱為 `fortifyScan` 的 shared library。
2. 建立各產品掃描 Pipeline Job，Jenkinsfile 指向對應的 `fortify/<project>/Jenkinsfile`。
3. 建立 release 總管 Job，Jenkinsfile 指向 `fortify/Jenkinsfile-release`。
4. 建立掃描總管 Job，Jenkinsfile 指向 `fortify/Jenkinsfile`。
5. 第一次執行總管或子專案 Job 時，Pipeline 會建立參數；重新進入 `Build with Parameters` 後才會執行實際流程。
6. 從掃描總管 Job 選擇 `git_branch_name` 與要掃描的產品；`master` 會使用 `uat` 環境，`sit` 會使用 `sit` 環境。
7. 若需單獨掃描某一產品，可直接觸發對應子 Job，並確認 `git_branch_name`、`env`、`GIT_URL`、`SHELL_NAME`、`JENKINS_JDK_TOOL` 參數正確。

## 新增或調整掃描專案

新增掃描專案時需同步調整：

1. 新增 `fortify/<project>/Jenkinsfile`，透過 `fortifyScan(gitUrl: ..., shellName: ...)` 指定目標 repo 與 shell。
2. 將對應 Fortify shell 放到 `fortify/shells/`，檔名需與 `shellName` 完全一致。
3. `fortifyScan` 會建立 `git_branch_name`、`env`、`GIT_URL`、`SHELL_NAME` 與 `JENKINS_JDK_TOOL` 參數；`git_branch_name` 控制 checkout 分支，`env` 控制掃描環境，兩者可不同，例如 `master` 搭配 `uat`。
4. 確認 shell 內的 `BUILDID`、`REPORT_PATH`、`FPR`、`PROJECTROOT0`、Gradle build 指令與 BIRT 報表產出符合 Jenkins Job 與目標專案。
5. 若需由掃描總管觸發，在 `fortify/Jenkinsfile` 加入 boolean 參數與 stage，並透過 `scanProject` 觸發與收集本次 Build。jar/war 須輸出到模組的 `build/libs/`，報告須符合上述四份報告的產物契約。
6. 若掃描前需 publish 共用模組，在 `fortify/Jenkinsfile-release` 加入對應 release stage。

## 本地驗證

需 Java 與 Groovy 2.4.x，可使用 `groovy tests/fortify-outputs.groovy`，或指定 Groovy runtime jar：

```sh
java -cp /path/to/groovy-2.4.21.jar groovy.ui.GroovyMain tests/fortify-outputs.groovy
```

測試以模擬的 Jenkins Build/Copy Artifact 服務與真實檔案操作，檢查指定 Build 編號收集、部分失敗、全部失敗、缺少或空白產物、複製失敗、取消、舊檔排除與模組同名檔案；另測試所有掃描 Shell 的報告產生區段。此測試不執行 Fortify 掃描，也不取代實際 Jenkins 的 Declarative/CPS、插件與權限驗證。

部署驗證時，先確認子 Job 已能歸檔 `scan-output/**`，再啟用新版總管。勾選 NCB、FAC 執行一次，檢查兩個壓縮檔只有這兩個專案；再測試一個失敗及全部失敗的情境，確認總管狀態與壓縮檔內容符合上述規則。
