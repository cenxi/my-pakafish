docker run -d -p 10095:10095 --name funasr-service `
  -e http_proxy=http://host.docker.internal:7890 `
  -e https_proxy=http://host.docker.internal:7890 `
  -v "D:/docker_funasr_models:/workspace/models" `
  registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-cpu-0.4.6 `
  bash -c "cd /workspace/FunASR/runtime && bash run_server.sh --download-model-dir /workspace/models --vad-dir damo/speech_fsmn_vad_zh-cn-16k-common-onnx --model-dir damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-onnx --punc-dir damo/punc_ct-transformer_zh-cn-common-vad_realtime-vocab272727-onnx --port 10095 --certfile 0 && sleep infinity"
