docker run -d -p 10095:10095 --name funasr-service `
  -v "D:/docker_funasr_models:/workspace/models" `
  registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-cpu-0.4.6 `
  bash -c "cd /workspace/FunASR/runtime/websocket/build/bin && ./funasr-wss-server --download-model-dir /workspace/models --vad-dir /workspace/models/damo/speech_fsmn_vad_zh-cn-16k-common-onnx --model-dir /workspace/models/damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-onnx --itn-dir /workspace/models/thuduj12/fst_itn_zh --hotword /workspace/models/hotwords.txt --lm-dir NONE --decoder-thread-num 1 --model-thread-num 1 --io-thread-num 1 --port 10095 --certfile 0"
