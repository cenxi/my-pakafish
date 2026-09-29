# My-Pakafish 象棋 AI 智能复盘与语音教练系统

`My-Pakafish` 是一款基于 **Pikafish（皮卡鱼）象棋引擎** 与 **大语言模型（LLM）** 的现代化中国象棋智能对弈、局势分析与语音教练系统。前端采用 Vue 3 构建交互式棋盘，后端基于 Spring Boot 驱动引擎深算与大模型分析，并集成了 FunASR 实时语音交互，支持语音实时对讲与复盘指导。

---

## 🌟 核心特性

- ♟️ **皮卡鱼（Pikafish）UCI 引擎驱动**：
  - 支持任意 FEN 局面解析与多线程深度搜索。
  - 支持 MultiPV（多候选招法推荐）、即时胜率/分数评估（centipawns）。
  - 基于 Server-Sent Events (SSE) 实时推送算力深度与候选着法走势。
  - 完善的断开连接自适应机制（客户端断开即时中止深算，节省算力）。

- 🧠 **大模型 AI 象棋教练**：
  - 接入 OpenAI 规范兼容的大模型（支持 DeepSeek、通义千问、Gemini 等）。
  - 根据当前盘面与皮卡鱼评估结果，提供通俗易懂的战术分析、走子建议与局势点评。

- 🎙️ **实时语音对讲与热词优化**：
  - 集成 FunASR 实时语音识别与 WebSocket 全双工流式交互。
  - 针对中国象棋定制了专属高权重热词词库（`hotwords.txt`），覆盖棋子、招法口诀（如“炮八平五”、“马二进三”）、经典开局及杀法战术。
  - 支持轻量化容器部署（精简内存占用至 ~350MB，避免云服务器 OOM）。

- 💻 **现代化响应式前端**：
  - 基于 Vue 3 + Vite + Element Plus + Pinia。
  - 支持桌面端与移动端自适应布局。
  - 支持着法历史回溯、FEN 导入导出、胜率走势折线图展示。

---

## 🛠️ 技术栈

| 模块 | 技术选型 | 说明 |
| :--- | :--- | :--- |
| **前端 (front)** | Vue 3 + Vite 6 + Element Plus + Pinia + ECharts | 象棋棋盘、实时教练对话、语音通话弹窗、走势分析图表 |
| **后端 (pakafish-server)** | Spring Boot 3 + Java 17/21 | UCI 引擎进程管理、SSE 流式推送、LLM 客户端、WebSocket |
| **象棋引擎** | Pikafish (UCI 协议) | 中国象棋最强开源引擎之一，NNUE 评估权重 |
| **语音识别** | FunASR Runtime SDK (Docker) | 极速流式 ASR，定制象棋热词注入 |

---

## 📁 目录结构

```text
my-pakafish/
├── front/                     # Vue 3 前端工程
│   ├── src/
│   │   ├── components/        # 棋盘组件、AI教练、语音通话弹窗等
│   │   ├── views/             # 桌面与移动端视图
│   │   └── ...
│   └── package.json
├── pakafish-server/           # Spring Boot 后端工程
│   ├── src/main/java/         # 引擎服务、API控制器、WebSocket处理器
│   └── src/main/resources/    # 配置文件与棋谱库
├── deploy/                    # 部署配置与模型说明
├── FUNASR_DOCKER_RUN.md       # FunASR 容器极速轻量启动指南
├── hotwords.txt               # 象棋专属热词偏置表
├── start-funasr.ps1           # Windows FunASR 启动脚本
└── pom.xml                    # Maven 顶层工程配置
```

---

## 🚀 快速上手

### 1. 环境准备
- **Java**：JDK 17 或 21
- **Node.js**：Node 18+ 与 npm / pnpm
- **Pikafish 引擎**：下载并准备好对应系统的 [Pikafish 可执行文件及 NNUE 权重文件](https://github.com/official-pikafish/Pikafish)
- **Docker**（可选，用于本地运行 FunASR 语音服务）

---

### 2. 后端配置与启动

1. 修改后端配置文件 `pakafish-server/src/main/resources/application.yml`：
   ```yaml
   pikafish:
     # 配置您的 Pikafish 引擎绝对路径与工作目录
     engine-path: "D:\\tools\\Pikafish\\Pikafish-Windows-x86-64-universal.exe"
     work-dir: "D:\\tools\\Pikafish"
     threads: 4
     hash: 512

   llm:
     openai:
       base-url: "https://api.deepseek.com/v1"  # 或您的 LLM 服务地址
       api-key: "your-api-key"
       model-name: "deepseek-chat"

   funasr:
     ws-url: "ws://127.0.0.1:10095"
   ```

2. 编译并运行后端：
   ```bash
   mvn clean package -DskipTests
   cd pakafish-server
   mvn spring-boot:run
   ```

---

### 3. 前端启动

```bash
cd front
npm install
npm run dev
```

启动后在浏览器访问控制台提示的地址（如 `http://localhost:5173`）即可使用。

---

### 4. 启动 FunASR 语音服务（可选）

请参考 [FUNASR_DOCKER_RUN.md](FUNASR_DOCKER_RUN.md)，使用轻量化参数运行 Docker 镜像：
```powershell
.\start-funasr.ps1
```

---

## 📄 License

[MIT License](LICENSE)
