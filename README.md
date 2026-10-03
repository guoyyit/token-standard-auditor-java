# Token Standard Auditor

本地合约标准符合性校验服务。输入合约的 ABI 与字节码，输出按严重级别分类的
符合性结论与定位信息。当前内置 ERC-20（同质化代币）与 ERC-721（收藏品类代币）
两套标准、各两个版本的规则集。

## 运行与测试

```bash
mvn spring-boot:run        # 启动服务（默认 8080 端口）
mvn test                   # 运行全部自动化测试
```

数据默认持久化在 `~/.token-standard-auditor/auditor.db`（SQLite），可通过
`auditor.data-dir` 配置覆盖。

## 规则模型

规则以 JSON 描述，按 `(standard, version)` 二元组注册到 `RuleSetRegistry`，
同一标准的多个版本并存。内置规则位于 `src/main/resources/rules/*.json`，
启动时加载；运行期可通过管理接口更新。

```json
{
  "id": "erc20-v1",
  "standard": "ERC20",
  "version": "v1",
  "defaultVersion": true,
  "functions": [
    {"name": "transfer", "inputs": ["address", "uint256"], "outputs": ["bool"],
     "stateMutability": "nonpayable", "required": true, "severity": "ERROR"}
  ],
  "events": [
    {"name": "Transfer", "required": true, "severity": "ERROR",
     "params": [{"name": "from", "type": "address", "indexed": true}]}
  ]
}
```

- `functions[]`：必需/可选函数签名（名称、规范化输入类型、返回类型、
  `stateMutability`）。`required=true` 的函数缺失时按 `severity` 报告。
- `events[]`：必需事件及其参数索引（`indexed`）规则，参数按位置逐一比对。
- `defaultVersion`：未声明版本时使用的默认规则集；每个标准恰有一个默认，
  未显式标记时取最先注册的版本，行为确定。

每次检查在**提交时**解析目标标准与版本并固定规则快照：运行中更新规则
（`PUT /api/rules/{standard}/{version}`）只影响之后提交的检查，进行中的
检查不受影响。规则内容参与缓存键计算，规则更新后相同合约会重新检查，
不会返回过期结论。

## 检查内容

- ABI 中是否声明了必需函数与事件（签名按规范化类型计算）。
- ABI 声明的函数返回类型、`stateMutability` 是否符合标准约定。
- 事件参数的 `indexed` 标记是否符合标准。
- ABI 声明的函数选择器（keccak256 前 4 字节）是否出现在字节码中——
  声明但未实现的函数报告为不合规（ERROR）。
- 事件 topic0（keccak256 全哈希）是否出现在字节码中。

## 严重级别

- `ERROR`：违反标准强制要求。包括必需函数/事件缺失、ABI 声明但字节码
  未实现、事件参数索引规则不符、事件从未被字节码引用。
- `WARNING`：偏离标准约定但不阻断接口调用。包括可选函数缺失、返回类型
  或 `stateMutability` 与标准不一致。
- `INFO`：建议性提示（如推荐实现的可选接口）。

同一合约在同一规则集下重复检查的结论完全一致：规则按固定顺序求值，
定位信息包含 ABI 元素签名或字节码偏移。

## 错误模型

输入无法解析时返回可区分的错误，不静默跳过：

- `ABI_PARSE_ERROR`：ABI 缺失、JSON 非法、元素缺少 `type`、未知元素类型、
  未知 ABI 类型。
- `BYTECODE_PARSE_ERROR`：字节码缺失、非法十六进制、长度为奇数。
- `UNKNOWN_STANDARD_VERSION`：未注册的标准或版本。
- `TIMEOUT` / `CANCELLED` / `INTERNAL_ERROR`：执行期终态。

## 缓存语义

缓存键 = `SHA-256(standard + version + 规则集内容哈希 + ABI + 字节码)`。
内容完全相同的合约直接复用已完成的结论（不再执行检查）；ABI、字节码或
规则内容任一变化都会得到新键并重新检查。并发提交相同内容时结论一致，
规则更新后不会命中旧缓存。

## 取消、超时与崩溃恢复

- 每个检查有超时（`auditor.check-timeout-ms`，默认 30s），超时进入
  `TIMEOUT` 终态；`POST /api/audits/{id}/cancel` 可取消未完成的检查。
- 取消/超时的检查只落一条完整的终态报告（空 findings + 错误信息），
  不会留下部分可见的报告；已完成的检查不受影响。
- 所有报告持久化在 SQLite。重启后已完成的报告仍可查询；启动时丢弃
  上次运行遗留的非终态记录（`PENDING`/`RUNNING`），不会出现不完整报告。

## REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/audits` | 提交单个合约，返回 `checkId` |
| POST | `/api/audits/batch` | 批量提交，按序返回 `checkIds` |
| GET | `/api/audits/{checkId}` | 完成返回报告（200），进行中返回状态（202），未知返回 404 |
| GET | `/api/audits/{checkId}/status` | 查询检查状态 |
| POST | `/api/audits/{checkId}/cancel` | 取消检查 |
| GET | `/api/rules` | 列出全部规则集 |
| GET | `/api/rules/{standard}/{version}` | 查询指定规则集 |
| PUT | `/api/rules/{standard}/{version}` | 更新/注册规则集（运行期生效） |

提交体示例：

```json
{
  "contractId": "my-token",
  "abi": "[{\"type\":\"function\", ...}]",
  "bytecode": "0x60806040...",
  "standard": "ERC20",
  "standardVersion": "v1"
}
```

`standardVersion` 可省略，省略时使用该标准的默认版本。

## 配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `auditor.data-dir` | `~/.token-standard-auditor` | SQLite 数据目录 |
| `auditor.check-timeout-ms` | `30000` | 单检查超时 |
| `auditor.executor-core-size` | `4` | 检查线程池核心大小 |
| `auditor.executor-max-size` | `8` | 检查线程池上限 |
| `auditor.executor-queue-capacity` | `100` | 排队容量 |
