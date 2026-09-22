# FunASR 容器启动指引

镜像：`registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-cpu-0.4.6`

### PowerShell 启动命令

```powershell
docker run -d -p 10095:10095 --name funasr-service `
  -e http_proxy=http://host.docker.internal:7890 `
  -e https_proxy=http://host.docker.internal:7890 `
  -v "D:/docker_funasr_models:/workspace/models" `
  registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-cpu-0.4.6 `
  bash -c "cd /workspace/FunASR/runtime && bash run_server.sh --download-model-dir /workspace/models --vad-dir damo/speech_fsmn_vad_zh-cn-16k-common-onnx --model-dir damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-onnx --punc-dir damo/punc_ct-transformer_zh-cn-common-vad_realtime-vocab272727-onnx --port 10095 && sleep infinity"
```

或直接执行项目根目录下的脚本：

```powershell
.\start-funasr.ps1
```

> **注意：**
> 1. 宿主机已开启 `7890` 代理，添加 `-e http_proxy=http://host.docker.internal:7890` 以确保容器内能顺利连接 ModelScope 下载模型；
> 2. 模型已自动挂载并缓存至 `D:/docker_funasr_models`，后续重启容器无需重新下载；
> 3. 末尾添加 `&& sleep infinity` 防止官方脚本后台派生后主进程退出导致容器停止。

### 常用运维命令

```powershell
# 查看实时日志（查看模型下载与运行状态）
docker logs -f funasr-service

# 停止 / 启动 / 重启 / 强制删除
docker stop funasr-service
docker start funasr-service
docker restart funasr-service
docker rm -f funasr-service
```
