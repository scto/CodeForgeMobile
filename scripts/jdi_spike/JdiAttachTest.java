import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.List;
import java.util.Map;

public class JdiAttachTest {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5005;
        System.out.println("Connecting to JDWP on port " + port + "...");

        VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        AttachingConnector socketConnector = null;
        for (AttachingConnector ac : vmm.attachingConnectors()) {
            if (ac.transport().name().equals("dt_socket")) {
                socketConnector = ac;
                break;
            }
        }
        if (socketConnector == null) {
            throw new IllegalStateException("SocketAttachingConnector not found");
        }

        Map<String, Connector.Argument> connArgs = socketConnector.defaultArguments();
        connArgs.get("hostname").setValue("127.0.0.1");
        connArgs.get("port").setValue(String.valueOf(port));

        VirtualMachine vm = socketConnector.attach(connArgs);
        System.out.println("Attached to VM: " + vm.name() + " " + vm.version());

        EventRequestManager erm = vm.eventRequestManager();
        ClassPrepareRequest cpr = erm.createClassPrepareRequest();
        cpr.addClassFilter("TargetApp");
        cpr.enable();

        vm.resume();

        EventQueue eventQueue = vm.eventQueue();
        boolean finished = false;

        while (!finished) {
            EventSet eventSet = eventQueue.remove(5000);
            if (eventSet == null) {
                System.out.println("Event timeout");
                break;
            }
            for (Event event : eventSet) {
                if (event instanceof VMDisconnectEvent || event instanceof VMDeathEvent) {
                    System.out.println("VM disconnected or died");
                    finished = true;
                    break;
                } else if (event instanceof ClassPrepareEvent) {
                    ClassPrepareEvent cpe = (ClassPrepareEvent) event;
                    ReferenceType refType = cpe.referenceType();
                    System.out.println("Class prepared: " + refType.name());

                    List<Location> locs = refType.locationsOfLine(11);
                    if (!locs.isEmpty()) {
                        Location loc = locs.get(0);
                        BreakpointRequest bpr = erm.createBreakpointRequest(loc);
                        bpr.enable();
                        System.out.println("Breakpoint set at line 11: " + loc);
                    } else {
                        System.err.println("No location found for line 11");
                    }
                } else if (event instanceof BreakpointEvent) {
                    BreakpointEvent bpe = (BreakpointEvent) event;
                    System.out.println("HIT Breakpoint at: " + bpe.location());
                    ThreadReference thread = bpe.thread();
                    System.out.println("Thread: " + thread.name() + " (status: " + thread.status() + ")");

                    StackFrame frame = thread.frame(0);
                    System.out.println("Top frame: " + frame.location());

                    List<LocalVariable> vars = frame.visibleVariables();
                    System.out.println("Visible local variables (" + vars.size() + "):");
                    for (LocalVariable var : vars) {
                        Value val = frame.getValue(var);
                        System.out.println("  " + var.name() + " (" + var.typeName() + ") = " + val);
                    }
                }
            }
            eventSet.resume();
        }

        System.out.println("JDI Spike completed successfully.");
    }
}
