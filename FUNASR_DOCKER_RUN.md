# FunASR 极速轻量容器启动指引（适配 4G 内存）

镜像：`registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-cpu-0.4.6`

### 为什么针对 4G 服务器做轻量优化？
阿里 FunASR 官方脚本默认加载了 `TLG.fst`（600MB 外部解码网格）和 `CT-Transformer`（接近 1GB 标点大模型），内存瞬间飙升至 **4.5GB+**，在 4G 服务器或本地 Docker 必定触发 Linux 内核 **OOM-Killed**（退出码 137）。

通过精简掉不必要的外部 LM（`--lm-dir NONE`）和标点大模型：
- **ASR 识别主模型（Paraformer）**：正常工作，自带强大声学+语言解码
- **VAD 语音端点检测（FSMN-VAD）**：正常工作，判断何时说完一句话
- **ITN 逆文本规整（FST-ITN）**：正常工作，规范数字与行棋叫法
- **内存占用仅约 350MB**，4G 内存云服务器完全无压力！

---

### Windows PowerShell 一键启动命令

```powershell
docker run -d -p 10095:10095 --name funasr-service `
  -v "D:/docker_funasr_models:/workspace/models" `
  registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-cpu-0.4.6 `
  bash -c "cd /workspace/FunASR/runtime/websocket/build/bin && ./funasr-wss-server --download-model-dir /workspace/models --vad-dir /workspace/models/damo/speech_fsmn_vad_zh-cn-16k-common-onnx --model-dir /workspace/models/damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-onnx --itn-dir /workspace/models/thuduj12/fst_itn_zh --lm-dir NONE --decoder-thread-num 1 --model-thread-num 1 --io-thread-num 1 --port 10095 --certfile 0"
```

或直接执行项目根目录下的脚本：
```powershell
.\start-funasr.ps1
```

---

### Linux 云服务器启动命令

将本地的 `D:/docker_funasr_models` 上传到服务器的 `/data/funasr_models` 目录后执行：

```bash
docker run -d -p 10095:10095 --name funasr-service \
  -v /data/funasr_models:/workspace/models \
  registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-cpu-0.4.6 \
  bash -c "cd /workspace/FunASR/runtime/websocket/build/bin && ./funasr-wss-server \
    --download-model-dir /workspace/models \
    --vad-dir /workspace/models/damo/speech_fsmn_vad_zh-cn-16k-common-onnx \
    --model-dir /workspace/models/damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-onnx \
    --itn-dir /workspace/models/thuduj12/fst_itn_zh \
    --lm-dir NONE \
    --decoder-thread-num 1 \
    --model-thread-num 1 \
    --io-thread-num 1 \
    --port 10095 \
    --certfile 0"
```

> **优势：**
> 1. 无需联网下载任何模型，秒级秒启（5秒内就绪）；
> 2. 内存常驻仅 **350MB 左右**，4G 内存云服务器可稳定运行。
