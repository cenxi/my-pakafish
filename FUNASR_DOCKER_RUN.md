# FunASR 极速轻量容器启动指引（适配 4G 内存 + 象棋热词优化）

镜像：`registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-cpu-0.4.6`

### 为什么做双重优化？
1. **轻量化（防 4G 内存 OOM 崩溃）**：
   精简掉不必要的外部 600MB TLG 解码网格（`--lm-dir NONE`）和 1GB 标点大模型，内存占用从 **4.5GB+** 骤降至 **~350MB**，避免 Linux 内核 OOM-Killed（退出码 137），在 4G 云服务器上轻快稳定运行。
2. **象棋专属热词偏置（`hotwords.txt`）**：
   注入了涵盖棋子（车马炮兵卒相仕帅）、经典开局（当头炮、屏风马、仙人指路等）、进退平招法口诀（炮八平五、马二进三等）及战术杀法（卧槽马、铁门栓等）的高权重热词，大幅解决同音字误识问题。

---

### Windows PowerShell 一键启动命令

```powershell
docker run -d -p 10095:10095 --name funasr-service `
  -v "D:/docker_funasr_models:/workspace/models" `
  registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-cpu-0.4.6 `
  bash -c "cd /workspace/FunASR/runtime/websocket/build/bin && ./funasr-wss-server `
    --download-model-dir /workspace/models `
    --vad-dir /workspace/models/damo/speech_fsmn_vad_zh-cn-16k-common-onnx `
    --model-dir /workspace/models/damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-onnx `
    --itn-dir /workspace/models/thuduj12/fst_itn_zh `
    --hotword /workspace/models/hotwords.txt `
    --lm-dir NONE `
    --decoder-thread-num 1 `
    --model-thread-num 1 `
    --io-thread-num 1 `
    --port 10095 `
    --certfile 0"
```

或直接执行项目根目录下的脚本：
```powershell
.\start-funasr.ps1
```

---

### Linux 云服务器启动命令

将本地 `D:/docker_funasr_models`（包含 `hotwords.txt`）上传至云服务器 `/data/funasr_models`：

```bash
docker run -d -p 10095:10095 --name funasr-service \
  -v /data/funasr_models:/workspace/models \
  registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-cpu-0.4.6 \
  bash -c "cd /workspace/FunASR/runtime/websocket/build/bin && ./funasr-wss-server \
    --download-model-dir /workspace/models \
    --vad-dir /workspace/models/damo/speech_fsmn_vad_zh-cn-16k-common-onnx \
    --model-dir /workspace/models/damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-onnx \
    --itn-dir /workspace/models/thuduj12/fst_itn_zh \
    --hotword /workspace/models/hotwords.txt \
    --lm-dir NONE \
    --decoder-thread-num 1 \
    --model-thread-num 1 \
    --io-thread-num 1 \
    --port 10095 \
    --certfile 0"
```
