package io.github.mbroughani81.flowgen;

import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.OTFCompileAnalysisInputLocation;
import sootup.core.graph.ControlFlowGraph;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.common.constant.*;
import sootup.core.jimple.common.expr.*;
import sootup.core.model.*;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.ClassType;
import sootup.java.core.*;
import sootup.java.core.views.JavaView;
import sootup.java.core.types.JavaClassType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class TraceGenerator {

    private final JavaView view;
    private final Gson gson;
    private static final int MAX_CALL_DEPTH = 3;
    private static final long DEFAULT_IO_COST_MS = 20;
    private static final long UNBOUNDED_LOOP_FACTOR = 100;

    public TraceGenerator(String classpathDirectory) {
        this.view = createView(classpathDirectory);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    private JavaView createView(String classpathDir) {
        Path path = Paths.get(classpathDir);
        AnalysisInputLocation inputLocation = new OTFCompileAnalysisInputLocation(path);
        return new JavaView(inputLocation);
    }

    /**
     * Main entry point: analyze a specific class for @IOSpec annotations
     */
    public List<MethodTraceSet> analyzeClass(String fullyQualifiedClassName) {
        List<MethodTraceSet> results = new ArrayList<>();

        JavaClassType classType = view.getIdentifierFactory().getClassType(fullyQualifiedClassName);
        if (!view.getClass(classType).isPresent()) {
            System.err.println("Class not found: " + fullyQualifiedClassName);
            return results;
        }

        JavaSootClass sootClass = view.getClass(classType).get();
        for (JavaSootMethod method : sootClass.getMethods()) {
            Optional<IOSpecAnnotation> ann = extractIOSpecAnnotation(method);
            if (ann.isPresent() && method.hasBody()) {
                MethodTraceSet set = analyzeMethod(method, method.getBody(), ann.get());
                results.add(set);
            }
        }
        return results;
    }

    private Optional<IOSpecAnnotation> extractIOSpecAnnotation(JavaSootMethod method) {
        for (AnnotationUsage ann : method.getAnnotations()) {
            if (ann.getAnnotation().getFullyQualifiedName().equals("io.github.mbroughani81.perfspec.IOSpec")) {
                return Optional.of(new IOSpecAnnotation(ann));
            }
        }
        return Optional.empty();
    }

    private MethodTraceSet analyzeMethod(SootMethod method, Body body, IOSpecAnnotation spec) {
        MethodTraceSet.SpecInfo info = new MethodTraceSet.SpecInfo();
        info.setType("IO");
        info.setMax(spec.getMax());
        info.setUnit(spec.getUnit());
        info.setPercentile(spec.getPercentile());

        List<MethodTraceSet.Trace> traces = extractTraces(method, body, spec);
        MethodTraceSet set = new MethodTraceSet();
        set.setMethod(method.getSignature().toString());
        set.setSpec(info);
        set.setTraces(traces);

        return set;
    }

    private List<MethodTraceSet.Trace> extractTraces(
            SootMethod rootMethod,
            Body rootBody,
            IOSpecAnnotation rootSpec) {
        System.out.println("extract traces for => " + rootMethod.toString());

        List<MethodTraceSet.Trace> result = new ArrayList<>();
        Stmt firstStmt = rootBody.getStmts().get(0);
        Set<Stmt> exits = findExitPoints(rootBody, rootBody.getControlFlowGraph());
        int[] idCounter = { 0 };

        for (Stmt exit : exits) {
            explorePath(rootMethod, rootBody, firstStmt, exit,
                    new ArrayList<>(), new HashSet<>(), 0,
                    rootSpec, new CostSnapshot(0, 0),
                    result, idCounter);
        }
        // assign final ids
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setId("flow_" + (i + 1));
        }
        return result;
    }

    private static class CostSnapshot {
        final long known;
        final long estimated;

        CostSnapshot(long k, long e) {
            this.known = k;
            this.estimated = e;
        }

        CostSnapshot add(long k, long e) {
            return new CostSnapshot(known + k, estimated + e);
        }

        long total() {
            return known + estimated;
        }
    }

    private void explorePath(SootMethod currentMethod,
            Body body,
            Stmt current,
            Stmt target,
            List<Stmt> prefix,
            Set<String> callStack,
            int depth,
            IOSpecAnnotation rootSpec,
            CostSnapshot cost,
            List<MethodTraceSet.Trace> results,
            int[] idCounter) {

        prefix.add(current);
        long[] inc = statementCostInContext(current, body, currentMethod);
        CostSnapshot newCost = cost.add(inc[0], inc[1]);

        if (current.equals(target)) {
            // reached exit – evaluate risk and possibly keep
            String risk = classifyRisk(newCost.total(), newCost.known, rootSpec.getMax());
            if (!risk.equals("LOW_RISK")) {
            // if (true) {
                MethodTraceSet.Trace trace = buildTrace(prefix, currentMethod.getSignature().toString(),
                        newCost, risk, idCounter[0]++);
                results.add(trace);
            }
        } else {
            ControlFlowGraph<?> cfg = body.getControlFlowGraph();

            for (Stmt succ : cfg.successors(current)) {
                if (prefix.contains(succ))
                    continue;

                // Case 1: Method invoke
                // JInterfaceInvokeExpr
                // JVirtualInvokeExpr
                // AbstractInstanceInvokeExpr
                Optional<AbstractInvokeExpr> succInvokeExprOpt = getInvokeExpr(succ);
                if (succInvokeExprOpt.isPresent() && depth < MAX_CALL_DEPTH) {
                    Set<JavaSootMethod> callees = resolveCallTargets(succInvokeExprOpt.get(), currentMethod);
                    for (JavaSootMethod callee : callees) {
                        // TODO
                        // Consider circular calls.
                        if (callee.hasBody() && !callStack.contains(callee.getSignature().toString())) {
                            System.out.println("caller => " + current.toString());
                            System.out.println("callee => " + callee.toString());
                            System.out.println("succ => " + succ.toString());
                            System.out.println("=================================");

                            if (isSinkMethod(callee)) {
                                explorePath(currentMethod, body, succ, target,
                                            prefix, callStack, depth, rootSpec, newCost, results, idCounter);
                                continue;
                            }

                            Body calleeBody = callee.getBody();
                            Stmt calleeFirst = calleeBody.getStmts().get(0);
                            Set<Stmt> calleeExits = findExitPoints(calleeBody, calleeBody.getControlFlowGraph());

                            Set<String> newStack = new HashSet<>(callStack);
                            newStack.add(callee.getSignature().toString());

                            for (Stmt calleeExit : calleeExits) {
                                // Clone prefix so far (immutable snapshot)
                                List<Stmt> splicedPrefix = new ArrayList<>(prefix);
                                // Phase 1: explore callee from entry to its exit, appending to splicedPrefix.
                                explorePath(callee, calleeBody, calleeFirst, calleeExit,
                                        splicedPrefix, newStack, depth + 1, rootSpec, newCost, results, idCounter);
                                // Phase 2: we will start from 'succ' with the path already
                                // containing the callee statements.
                                // This works as long as we do not re‑enter the same call. The visited set
                                // (prefix) will prevent looping inside the same method.
                                explorePath(currentMethod, body, succ, target,
                                        splicedPrefix, callStack, depth, rootSpec, newCost, results, idCounter);
                            }
                            // do NOT fall through to normal intra-procedural step
                            continue;
                        }
                    }
                }
                // Case 2: normal
                explorePath(currentMethod, body, succ, target,
                        prefix, callStack, depth, rootSpec, newCost, results, idCounter);
            }
        }
        prefix.remove(prefix.size() - 1); // backtrack
    }

    private Optional<AbstractInvokeExpr> getInvokeExpr(Stmt stmt) {
        if (stmt instanceof JInvokeStmt) {
            return ((JInvokeStmt) stmt).getInvokeExpr();
        }
        if (stmt instanceof JAssignStmt) {
            JAssignStmt assign = (JAssignStmt) stmt;
            if (assign.getRightOp() instanceof AbstractInvokeExpr) {
                return Optional.of((AbstractInvokeExpr) assign.getRightOp());
            }
        }
        return Optional.empty();
    }

    private boolean isSinkMethod(JavaSootMethod method) {
        Optional<IOSpecAnnotation> ann = extractIOSpecAnnotation(method);
        return ann.isPresent() && ann.get().isSink();
    }

    // TODO
    // Currently doesn't exstimate the loop costs correctly.
    // This is critical! Must be fixed.
    private long[] statementCostInContext(Stmt stmt, Body body, SootMethod method) {
        long loopMultiplier = 1;
        if (isStmtInLoop(stmt, body)) {
            loopMultiplier = UNBOUNDED_LOOP_FACTOR;
        }

        long known = 0;
        long est = 0;

        Optional<AbstractInvokeExpr> invokeExpr = getInvokeExpr(stmt);
        if (invokeExpr.isPresent()) {
            Set<JavaSootMethod> targets = resolveCallTargets(invokeExpr.get(), method);
            for (JavaSootMethod tgt : targets) {
                Optional<IOSpecAnnotation> ann = extractIOSpecAnnotation(tgt);
                if (ann.isPresent()) {
                    known += ann.get().getMax();
                } else {
                    if (isBlockingCall(tgt)) {
                        est += DEFAULT_IO_COST_MS;
                    }
                }
            }
        } else {
            // non‑invoke: check for blocking I/O pattern
            String s = stmt.toString().toLowerCase();
            if (s.contains("read") || s.contains("write") || s.contains("wait") ||
                    s.contains("network") || s.contains("database")) {
                est += DEFAULT_IO_COST_MS;
            }
        }

        return new long[] { known * loopMultiplier, est * loopMultiplier };
    }

    private boolean isStmtInLoop(Stmt stmt, Body body) {
        ControlFlowGraph<?> cfg = body.getControlFlowGraph();
        Set<Stmt> visited = new HashSet<>();
        Deque<Stmt> stack = new ArrayDeque<>();

        for (Stmt succ : cfg.successors(stmt)) {
            if (succ.equals(stmt)) {
                return true; // self‑loop
            }
            visited.clear();
            stack.clear();
            stack.push(succ);
            visited.add(succ);
            while (!stack.isEmpty()) {
                Stmt current = stack.pop();
                if (current.equals(stmt)) {
                    return true; // back‑edge found
                }
                for (Stmt next : cfg.successors(current)) {
                    if (!visited.contains(next)) {
                        visited.add(next);
                        stack.push(next);
                    }
                }
            }
        }
        return false;
    }

    private boolean isBlockingCall(SootMethod method) {
        String sig = method.getSignature().toString().toLowerCase();
        return sig.contains("read") || sig.contains("write") || sig.contains("wait") ||
                sig.contains("select") || sig.contains("poll");
    }

    private String classifyRisk(long totalCost, long knownCost, long max) {
        if (knownCost > max)
            return "GUARANTEED_VIOLATION";
        if (totalCost >= max)
            return "SUSPECTED";
        return "LOW_RISK";
    }

    private MethodTraceSet.Trace buildTrace(List<Stmt> stmts, String methodSig,
            CostSnapshot cost, String risk, int id) {
        MethodTraceSet.Trace trace = new MethodTraceSet.Trace();
        trace.setId("flow_" + id);
        trace.setPath(stmts.stream()
                .map(stmt -> "line "
                        + (stmt.getPositionInfo().getStmtPosition() != null
                                ? stmt.getPositionInfo().getStmtPosition().getFirstLine()
                                : -1)
                        +
                        ": " + stmt)
                .collect(Collectors.toList()));
        trace.setTags(deriveTags(stmts, risk));
        trace.setEstimatedCost(cost.total());
        trace.setKnownCost(cost.known);
        trace.setRiskLevel(risk);
        return trace;
    }

    private List<String> deriveTags(List<Stmt> stmts, String risk) {
        List<String> tags = new ArrayList<>();
        if (risk.equals("GUARANTEED_VIOLATION"))
            tags.add("GUARANTEED_VIOLATION");
        else if (risk.equals("SUSPECTED"))
            tags.add("SUSPECTED");
        return tags;
    }

    // ─────────────────────── CALL RESOLUTION (CHA) ───────────────────────
    private Set<JavaSootMethod> resolveCallTargets(AbstractInvokeExpr invokeExpr, SootMethod caller) {
        MethodSignature methodSig = invokeExpr.getMethodSignature();

        // static or special -> single target
        if (invokeExpr instanceof JStaticInvokeExpr || invokeExpr instanceof JSpecialInvokeExpr) {
            return view.getMethod(methodSig)
                    .map(Collections::singleton)
                    .orElse(Collections.emptySet());
        }

        // virtual / interface
        Set<JavaSootMethod> targets = new HashSet<>();
        JavaClassType declaringClass = (JavaClassType) methodSig.getDeclClassType();

        view.getClasses().forEach(sc -> {
            ClassType classType = sc.getType();
            if (classType instanceof JavaClassType) {
                JavaClassType javaClassType = (JavaClassType) classType;
                if (isSubtype(javaClassType, declaringClass)) {
                    sc.getMethods().stream()
                            .filter(m -> methodSig.getSubSignature().equals(m.getSignature().getSubSignature()))
                            .forEach(targets::add);
                }
            }
        });
        return targets;
    }

    private boolean isSubtype(JavaClassType sub, JavaClassType sup) {
        // simple hierarchy check: view.getTypeHierarchy() may not be available in basic
        // SootUp.
        // For PoC we use string‑based check on class hierarchy (or load hierarchy on
        // first use)
        if (sub.equals(sup))
            return true;
        // try to get superclass
        Optional<JavaClassType> superClass = view.getClass(sub)
                .flatMap(sc -> sc.getSuperclass())
                .map(sig -> view.getIdentifierFactory().getClassType(sig.getFullyQualifiedName()));
        if (superClass.isPresent() && isSubtype(superClass.get(), sup))
            return true;
        // also check interfaces
        JavaSootClass clazz = view.getClass(sub).orElse(null);
        if (clazz != null) {
            // getInterfaces() returns Set<? extends ClassType>, so we need to check each
            // element
            for (ClassType iface : clazz.getInterfaces()) {
                // Only consider JavaClassType instances
                if (iface instanceof JavaClassType) {
                    if (isSubtype((JavaClassType) iface, sup))
                        return true;
                }
            }
        }
        return false;
    }

    // ─────────────────────── EXIT POINTS ─────────────────────────────────

    private Set<Stmt> findExitPoints(Body body, ControlFlowGraph<?> cfg) {
        Set<Stmt> exits = new HashSet<>();
        for (Stmt stmt : body.getStmts()) {
            if (stmt instanceof JReturnStmt || stmt instanceof JThrowStmt) {
                exits.add(stmt);
            } else if (stmt instanceof JInvokeStmt && cfg.outDegree(stmt) == 0) {
                exits.add(stmt);
            }
        }
        return exits;
    }

    // Inner class to hold annotation data
    private static class IOSpecAnnotation {
        private final long max;
        private final String unit;
        private final boolean sink;
        private final int percentile;
        private final String desc;

        public IOSpecAnnotation(AnnotationUsage annotation) {
            // Extract values from annotation with proper type handling
            this.max = getLongValue(annotation, "max", 100L);
            this.unit = getStringValue(annotation, "unit", "ms");
            this.sink = getBooleanValue(annotation, "sink", false);
            this.percentile = 100;
            this.desc = getStringValue(annotation, "desc", "");
        }

        private String getStringValue(AnnotationUsage annotation, String key, String defaultValue) {
            for (Map.Entry<String, Object> entry : annotation.getValues().entrySet()) {
                if (entry.getKey().equals(key)) {
                    Object value = entry.getValue();
                    if (value instanceof StringConstant) {
                        return ((StringConstant) value).getValue();
                    }
                    return value.toString();
                }
            }
            return defaultValue;
        }

        private long getLongValue(AnnotationUsage annotation, String key, long defaultValue) {
            for (Map.Entry<String, Object> entry : annotation.getValues().entrySet()) {
                if (entry.getKey().equals(key)) {
                    Object value = entry.getValue();
                    if (value instanceof LongConstant) {
                        return ((LongConstant) value).getValue();
                    } else if (value instanceof Long) {
                        return (Long) value;
                    } else if (value instanceof Integer) {
                        return ((Integer) value).longValue();
                    }
                }
            }
            return defaultValue;
        }

        private boolean getBooleanValue(AnnotationUsage annotation, String key, boolean defaultValue) {
            System.out.println("Annotation: " + annotation.getAnnotation().getFullyQualifiedName());
            for (Map.Entry<String, Object> entry : annotation.getValues().entrySet()) {
                if (entry.getKey().equals(key)) {
                    Object value = entry.getValue();
                    if (value instanceof BooleanConstant) {
                        return ((BooleanConstant) value).getValue();
                    }
                }
            }
            return defaultValue;
        }

        public long getMax() {
            return max;
        }

        public String getUnit() {
            return unit;
        }

        public boolean isSink() {
            return sink;
        }

        public int getPercentile() {
            return percentile;
        }

        public String getDesc() {
            return desc;
        }
    }
}
