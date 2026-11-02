import subprocess, json, sys, threading, queue, time
p = subprocess.Popen(["java","-cp","out","CodeForgeDapAdapter"],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=sys.stderr,env={"PATH":"/usr/bin:/bin"})
q = queue.Queue(); seq=[0]
def rd():
    f=p.stdout
    while True:
        h=b""
        while not h.endswith(b"\r\n\r\n"):
            c=f.read(1)
            if not c: return
            h+=c
        n=int(h.split(b":")[1].split(b"\r\n")[0]); q.put(json.loads(f.read(n)))
threading.Thread(target=rd,daemon=True).start()
def req(cmd,args=None):
    seq[0]+=1; m={"seq":seq[0],"type":"request","command":cmd,"arguments":args or {}}
    b=json.dumps(m).encode(); p.stdin.write(b"Content-Length: %d\r\n\r\n"%len(b)+b); p.stdin.flush(); return seq[0]
def wait(pred,t=15):
    end=time.time()+t
    while time.time()<end:
        try: m=q.get(timeout=0.5)
        except queue.Empty: continue
        print("<-",json.dumps(m)[:230])
        if pred(m): return m
    raise SystemExit("TIMEOUT")
def call(cmd,args=None):
    s=req(cmd,args); return wait(lambda m:m.get("request_seq")==s)
call("initialize",{"adapterID":"x"})
call("launch",{"mainClass":"demo.App","classPaths":["/tmp/claude-0/dap/t"],"cwd":"/tmp/claude-0/dap/t"})
wait(lambda m:m.get("event")=="initialized")
r=call("setBreakpoints",{"source":{"path":"/tmp/claude-0/dap/t/demo/App.java"},"breakpoints":[{"line":11}]})
call("configurationDone")
st=wait(lambda m:m.get("event")=="stopped")
tid=st["body"]["threadId"]
fr=call("stackTrace",{"threadId":tid})["body"]["stackFrames"]
sc=call("scopes",{"frameId":fr[0]["id"]})["body"]["scopes"]
call("variables",{"variablesReference":sc[0]["variablesReference"]})
call("evaluate",{"frameId":fr[0]["id"],"expression":"x"})
call("next",{"threadId":tid}); wait(lambda m:m.get("event")=="stopped")
call("continue",{"threadId":tid})
wait(lambda m:m.get("event")=="terminated")
call("disconnect")
print("OK")
