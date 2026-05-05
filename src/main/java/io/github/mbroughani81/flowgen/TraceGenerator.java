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
    private static final int MAX_LOOP_UNROLL = 3; // how many times we traverse a loop back‑edge
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

    // ----------------------------------------------------------------------
    // Public entry point
    // ----------------------------------------------------------------------

    public List<MethodTraceSet> analyzeClass(String fullyQualifiedClassName) {
        List<MethodTraceSet> results = new ArrayList<>();
        JavaClassType classType = view.getIdentifierFactory().getClassType(fullyQualifiedClassName);
        if (!view.getClass(classType).isPresent()) {
            System.err.println("Class not found: " + fullyQualifiedClassName);
            return results;
        }

        JavaSootClass sootClass = view.getClass(classType).get();
        for (JavaSootMethod method : sootClass.getMethods()) {
            Optional<MSpecAnnotation> ann = extractMSpecAnnotation(method);
            if (ann.isPresent() && method.hasBody()) {
                MethodTraceSet set = analyzeMethod(method, method.getBody(), ann.get());
                results.add(set);
            }
        }
        return results;
    }

    // ----------------------------------------------------------------------
    // Annotation extraction
    // ----------------------------------------------------------------------

    private Optional<MSpecAnnotation> extractMSpecAnnotation(JavaSootMethod method) {
        for (AnnotationUsage ann : method.getAnnotations()) {
            if (ann.getAnnotation().getFullyQualifiedName().equals("io.github.mbroughani81.perfspec.MSpec")) {
                return Optional.of(new MSpecAnnotation(ann));
            }
        }
        return Optional.empty();
    }

    // ----------------------------------------------------------------------
    // Main analysis of a single method
    // ----------------------------------------------------------------------

    private MethodTraceSet analyzeMethod(SootMethod method, Body body, MSpecAnnotation spec) {
        MethodTraceSet.SpecInfo info = new MethodTraceSet.SpecInfo();
        info.setType("IO");
        info.setMax(spec.getMax());
        info.setUnit(spec.getUnit());
        info.setPercentile(spec.getPercentile());

        List<MethodTraceSet.Trace> allTraces = exploreMethod(method, body, new HashSet<>(), 0);

        List<MethodTraceSet.Trace> violatingTraces = allTraces.stream()
                .filter(trace ->
                // true
                riskClassify(trace.getKnownCost(), trace.getEstimatedCost(), spec.getMax())
                        .equals("GUARANTEED_VIOLATION"))
                .collect(Collectors.toList());

        // Assign ids
        int id = 1;
        for (MethodTraceSet.Trace trace : violatingTraces) {
            trace.setId("flow_" + id++);
        }

        MethodTraceSet set = new MethodTraceSet();
        set.setMethod(method.getSignature().toString());
        set.setSpec(info);
        set.setTraces(violatingTraces);
        return set;
    }

    // ----------------------------------------------------------------------
    // Flow (trace) extraction – the corrected heart of the generator
    // ----------------------------------------------------------------------

    private List<MethodTraceSet.Trace> exploreMethod(SootMethod method, Body body,
            Set<String> callStack,
            int depth) {
        List<MethodTraceSet.Trace> traces = new ArrayList<>();
        Stmt entry = body.getStmts().get(0);
        Set<Stmt> exits = findExitPoints(body, body.getControlFlowGraph());

        // DFS from entry, building traces
        explorePath(method, body, entry, exits,
                new ArrayList<>(), callStack, depth, new HashMap<>(),
                new CostSnapshot(0, 0), traces);
        return traces;
    }

    private void explorePath(SootMethod currentMethod,
            Body body,
            Stmt current,
            Set<Stmt> exitPoints,
            List<Stmt> currentPath,
            Set<String> callStack,
            int depth,
            Map<Stmt, Integer> loopCounter,
            CostSnapshot cost,
            List<MethodTraceSet.Trace> outTraces) {
        // add to currentPath
        currentPath.add(current);
        // Update cost
        long[] inc = statementCostInContext(current, body, currentMethod, currentPath);
        cost = cost.add(inc[0], inc[1]);

        // Exit point reached -> terminal trace
        if (exitPoints.contains(current)) {
            MethodTraceSet.Trace trace = buildTrace(currentPath, cost);
            outTraces.add(trace);

            currentPath.remove(currentPath.size() - 1);
            return;
        } else {
            ControlFlowGraph<?> cfg = body.getControlFlowGraph();
            int visitCount = loopCounter.getOrDefault(current, 0);
            // IMMIDIATE EXIT
            // TODO
            // I don't like this...
            if (visitCount >= MAX_LOOP_UNROLL) {
                currentPath.remove(currentPath.size() - 1);
                return;
            }
            // Update loopCounter. DO NOT MOVE!!!
            loopCounter.put(current, loopCounter.getOrDefault(current, 0) + 1);

            Optional<AbstractInvokeExpr> invokeExpr = getInvokeExpr(current);
            if (invokeExpr.isPresent() && depth < MAX_CALL_DEPTH) {
                Set<JavaSootMethod> callees = resolveCallTargets(invokeExpr.get(), currentMethod);
                for (JavaSootMethod callee : callees) {
                    if (callStack.contains(callee.getSignature().toString())) {
                        break;
                    }
                    if (isSinkMethod(callee)) {
                        break;
                    }
                    if (callee.hasBody()) {
                        Set<String> newStack = new HashSet<>(callStack);
                        newStack.add(callee.getSignature().toString());
                        List<MethodTraceSet.Trace> calleeTraces = exploreMethod(callee,
                                callee.getBody(),
                                newStack,
                                depth + 1);
                        for (MethodTraceSet.Trace calleeTrace : calleeTraces) {
                            List<Stmt> combined = new ArrayList<>(currentPath);
                            combined.addAll(calleeTrace.path);
                            CostSnapshot newCost = cost.add(calleeTrace.knownCost, calleeTrace.estimatedCost);
                            for (Stmt succ : cfg.successors(current)) {
                                HashMap<Stmt, Integer> newLoopCounter = new HashMap<>(loopCounter);
                                explorePath(currentMethod, body, succ, exitPoints,
                                        combined, callStack, depth, newLoopCounter, newCost, outTraces);
                            }
                        }
                        // TODO check if can be deleted
                        if (calleeTraces.size() > 0) {
                            currentPath.remove(currentPath.size() - 1);
                            return;
                        }
                    }
                }
            }

            for (Stmt succ : cfg.successors(current)) {
                HashMap<Stmt, Integer> newLoopCounter = new HashMap<>(loopCounter);
                explorePath(currentMethod, body, succ, exitPoints,
                        currentPath, callStack, depth, newLoopCounter, cost, outTraces);
            }
        }

        // reset currentPath
        currentPath.remove(currentPath.size() - 1);
    }

    // ----------------------------------------------------------------------
    // Helper methods for statements, loops, calls, costs
    // ----------------------------------------------------------------------

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
        Optional<MSpecAnnotation> ann = extractMSpecAnnotation(method);
        return ann.isPresent() && ann.get().isSink();
    }

    private MethodTraceSet.Trace buildTrace(List<Stmt> statements, CostSnapshot cost) {
        MethodTraceSet.Trace trace = new MethodTraceSet.Trace();
        trace.setPath(new ArrayList<>(statements));
        trace.setKnownCost(cost.known);
        trace.setEstimatedCost(cost.total());
        trace.setRiskLevel(riskClassify(cost.known, cost.estimated, -1));
        return trace;
    }

    private long[] statementCostInContext(Stmt stmt, Body body, SootMethod method, List<Stmt> currentPath) {
        long loopMultiplier = 1;
        int occurrence = (int) currentPath.stream().filter(s -> s.equals(stmt)).count();
        if (occurrence > 1) {
            loopMultiplier = UNBOUNDED_LOOP_FACTOR; // we saw it twice => inside a loop
        }

        long known = 0;
        long est = 0;

        Optional<AbstractInvokeExpr> invokeExpr = getInvokeExpr(stmt);
        if (invokeExpr.isPresent()) {
            Set<JavaSootMethod> targets = resolveCallTargets(invokeExpr.get(), method);
            for (JavaSootMethod tgt : targets) {
                Optional<MSpecAnnotation> ann = extractMSpecAnnotation(tgt);
                if (ann.isPresent()) {
                    known += ann.get().getMax();
                }
            }
        }

        return new long[] { known * loopMultiplier, est * loopMultiplier };
    }

    private String riskClassify(long knownCost, long totalCost, long max) {
        if (knownCost > max)
            return "GUARANTEED_VIOLATION";
        if (totalCost >= max)
            return "SUSPECTED";
        return "LOW_RISK";
    }

    // ----------------------------------------------------------------------
    // Call resolution (CHA)
    // ----------------------------------------------------------------------

    private Set<JavaSootMethod> resolveCallTargets(AbstractInvokeExpr invokeExpr, SootMethod caller) {
        MethodSignature methodSig = invokeExpr.getMethodSignature();
        if (invokeExpr instanceof JStaticInvokeExpr || invokeExpr instanceof JSpecialInvokeExpr) {
            return view.getMethod(methodSig).map(Collections::singleton).orElse(Collections.emptySet());
        }

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
        if (sub.equals(sup))
            return true;
        Optional<JavaClassType> superClass = view.getClass(sub)
                .flatMap(sc -> sc.getSuperclass())
                .map(sig -> view.getIdentifierFactory().getClassType(sig.getFullyQualifiedName()));
        if (superClass.isPresent() && isSubtype(superClass.get(), sup))
            return true;
        JavaSootClass clazz = view.getClass(sub).orElse(null);
        if (clazz != null) {
            for (ClassType iface : clazz.getInterfaces()) {
                if (iface instanceof JavaClassType && isSubtype((JavaClassType) iface, sup))
                    return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------------
    // Exit points in a method body
    // ----------------------------------------------------------------------

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

    // ----------------------------------------------------------------------
    // Inner class for MSpec annotation data
    // ----------------------------------------------------------------------

    private static class MSpecAnnotation {
        private final long max;
        private final String unit;
        private final boolean sink;
        private final int percentile;
        private final String desc;

        public MSpecAnnotation(AnnotationUsage annotation) {
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
                    if (value instanceof StringConstant)
                        return ((StringConstant) value).getValue();
                    return value.toString();
                }
            }
            return defaultValue;
        }

        private long getLongValue(AnnotationUsage annotation, String key, long defaultValue) {
            for (Map.Entry<String, Object> entry : annotation.getValues().entrySet()) {
                if (entry.getKey().equals(key)) {
                    Object value = entry.getValue();
                    if (value instanceof LongConstant)
                        return ((LongConstant) value).getValue();
                    if (value instanceof Long)
                        return (Long) value;
                    if (value instanceof Integer)
                        return ((Integer) value).longValue();
                }
            }
            return defaultValue;
        }

        private boolean getBooleanValue(AnnotationUsage annotation, String key, boolean defaultValue) {
            for (Map.Entry<String, Object> entry : annotation.getValues().entrySet()) {
                if (entry.getKey().equals(key) && entry.getValue() instanceof BooleanConstant) {
                    return ((BooleanConstant) entry.getValue()).getValue();
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
