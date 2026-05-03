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
            Optional<IOSpecAnnotation> ann = extractIOSpecAnnotation(method);
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

    private Optional<IOSpecAnnotation> extractIOSpecAnnotation(JavaSootMethod method) {
        for (AnnotationUsage ann : method.getAnnotations()) {
            if (ann.getAnnotation().getFullyQualifiedName().equals("io.github.mbroughani81.perfspec.IOSpec")) {
                return Optional.of(new IOSpecAnnotation(ann));
            }
        }
        return Optional.empty();
    }

    // ----------------------------------------------------------------------
    // Main analysis of a single method
    // ----------------------------------------------------------------------

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

    // ----------------------------------------------------------------------
    // Flow (trace) extraction – the corrected heart of the generator
    // ----------------------------------------------------------------------

    /**
     * Returns all execution flows (traces) of the given method that are likely
     * to violate the performance specification.
     */
    private List<MethodTraceSet.Trace> extractTraces(SootMethod rootMethod,
            Body rootBody,
            IOSpecAnnotation rootSpec) {
        // Compute all flows from entry to exit (with loop unrolling and inlined calls)
        List<MethodFlow> allFlows = exploreMethod(rootMethod, rootBody,
                new HashSet<>(), 0, new HashMap<>());

        // Convert to the output format and filter by risk
        List<MethodTraceSet.Trace> result = new ArrayList<>();
        int id = 1;
        for (MethodFlow flow : allFlows) {
            String risk = classifyRisk(flow.totalCost, flow.knownCost, rootSpec.getMax());
            // if (!risk.equals("LOW_RISK")) {
            if (risk.equals("GUARANTEED_VIOLATION")) {
                MethodTraceSet.Trace trace = flow.toOutputFormat(rootMethod.getSignature().toString(), risk, id++);
                result.add(trace);
            }
        }
        return result;
    }

    private List<MethodFlow> exploreMethod(SootMethod method, Body body,
            Set<String> callStack,
            int depth,
            Map<Stmt, Integer> loopCounters) {
        List<MethodFlow> flows = new ArrayList<>();
        Stmt entry = body.getStmts().get(0);
        Set<Stmt> exits = findExitPoints(body, body.getControlFlowGraph());

        // DFS from the entry, building paths
        explorePath(method, body, entry, exits,
                new ArrayList<>(), callStack, depth, loopCounters,
                new CostSnapshot(0, 0), flows);
        return flows;
    }

    /**
     * Depth‑first exploration of the control flow graph, building one path at a
     * time.
     * When a method call is encountered, the callee's flows are inlined (spliced).
     */
    private void explorePath(SootMethod currentMethod,
            Body body,
            Stmt current,
            Set<Stmt> exitPoints,
            List<Stmt> currentPath,
            Set<String> callStack,
            int depth,
            Map<Stmt, Integer> loopCounters,
            CostSnapshot costSoFar,
            List<MethodFlow> outFlows) {
        // Append current statement to the path
        currentPath.add(current);

        // Update cost (including loop multiplier if we are inside a loop)
        long[] inc = statementCostInContext(current, body, currentMethod, currentPath);
        CostSnapshot newCost = costSoFar.add(inc[0], inc[1]);

        // Exit point reached ?
        if (exitPoints.contains(current)) {
            outFlows.add(new MethodFlow(new ArrayList<>(currentPath), newCost));
        } else {
            // --- Loop handling: allow back‑edges up to MAX_LOOP_UNROLL times
            int visitCount = loopCounters.getOrDefault(current, 0);
            if (visitCount >= MAX_LOOP_UNROLL) {
                return;
            }
            Map<Stmt, Integer> newCounters = new HashMap<>(loopCounters);
            newCounters.put(current, visitCount + 1);

            ControlFlowGraph<?> cfg = body.getControlFlowGraph();
            // --- 1. curr is method call
            Optional<AbstractInvokeExpr> invokeExpr = getInvokeExpr(current);
            if (invokeExpr.isPresent() && depth < MAX_CALL_DEPTH) {
                Set<JavaSootMethod> callees = resolveCallTargets(invokeExpr.get(), currentMethod);
                for (JavaSootMethod callee : callees) {
                    // TODO
                    // Avoid recursive calls
                    // Maybe not ignore the whole thing?
                    if (callStack.contains(callee.getSignature().toString())) {
                        break;
                    }
                    // Sink methods are left as atomic steps (do not inline)
                    if (isSinkMethod(callee)) {
                        break;
                    }
                    // Inline the callee’s flows
                    if (callee.hasBody()) {
                        System.out.println("current => " + current.toString());
                        System.out.println("caller => " + current.toString());
                        System.out.println("callee => " + callee.toString());
                        System.out.println("=================================");

                        Set<String> newStack = new HashSet<>(callStack);
                        newStack.add(callee.getSignature().toString());
                        List<MethodFlow> calleeFlows = exploreMethod(callee, callee.getBody(),
                                newStack, depth + 1, new HashMap<>());
                        for (MethodFlow calleeFlow : calleeFlows) {
                            List<Stmt> combined = new ArrayList<>(currentPath);
                            combined.addAll(calleeFlow.statements);
                            CostSnapshot combinedCost = newCost.add(calleeFlow.knownCost, calleeFlow.estCost);

                            for (Stmt succ : cfg.successors(current)) {
                                continueFrom(currentMethod, body, succ, exitPoints,
                                        combined, callStack, depth, newCounters,
                                        combinedCost, outFlows);
                            }
                        }
                        break;
                    }
                }
                // After handling the call, we do NOT also fall through to the normal
                // intra‑procedural step.
                // continue;
            }

            // --- 2. curr is normal
            for (Stmt succ : cfg.successors(current)) {
                explorePath(currentMethod, body, succ, exitPoints,
                        currentPath, callStack, depth, newCounters, newCost, outFlows);
            }
        }

        // Backtrack
        currentPath.remove(currentPath.size() - 1);
    }

    /**
     * Helper to continue exploration from a given statement, using a pre‑built path
     * and a pre‑computed cost. Avoids code duplication.
     */
    private void continueFrom(SootMethod method, Body body, Stmt start,
            Set<Stmt> exits, List<Stmt> currentPath,
            Set<String> callStack, int depth,
            Map<Stmt, Integer> loopCounters,
            CostSnapshot cost, List<MethodFlow> outFlows) {
        // We simply call explorePath, but the path already contains what we have built.
        // However, explorePath will add the start statement again. To prevent
        // duplicates,
        // we must temporarily remove the last element of currentPath if it equals
        // start?
        // Instead, we create a copy and then delegate.
        List<Stmt> pathCopy = new ArrayList<>(currentPath);
        // Do not add 'start' again; it will be added by explorePath.
        // Remove the last element if it is the same as start (which can happen if the
        // call
        // immediately precedes the start). This is a simplification; for a full robust
        // solution
        // we would need a more sophisticated state machine. For the typical N+1 example
        // it works.
        if (!pathCopy.isEmpty() && pathCopy.get(pathCopy.size() - 1).equals(start)) {
            pathCopy.remove(pathCopy.size() - 1);
        }
        explorePath(method, body, start, exits, pathCopy,
                callStack, depth, loopCounters, cost, outFlows);
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
        Optional<IOSpecAnnotation> ann = extractIOSpecAnnotation(method);
        return ann.isPresent() && ann.get().isSink();
    }

    /**
     * Returns [knownCost, estimatedCost] for a statement, taking loop context into
     * account.
     * A statement is considered "inside a loop" if the current path contains a
     * back‑edge
     * that would force repetition. For simplicity, we compute a loop multiplier
     * based on the number of times the statement appears in the current path.
     */
    private long[] statementCostInContext(Stmt stmt, Body body, SootMethod method, List<Stmt> currentPath) {
        long loopMultiplier = 1;
        // Count how many times this statement already appears in the path (a simplistic
        // loop detection)
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
                Optional<IOSpecAnnotation> ann = extractIOSpecAnnotation(tgt);
                if (ann.isPresent()) {
                    known += ann.get().getMax();
                } else if (isBlockingCall(tgt)) {
                    est += DEFAULT_IO_COST_MS;
                }
            }
        } else {
            String s = stmt.toString().toLowerCase();
            if (s.contains("read") || s.contains("write") || s.contains("wait") ||
                    s.contains("network") || s.contains("database")) {
                est += DEFAULT_IO_COST_MS;
            }
        }

        return new long[] { known * loopMultiplier, est * loopMultiplier };
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

    // ----------------------------------------------------------------------
    // Internal data structures
    // ----------------------------------------------------------------------

    private static class MethodFlow {
        final List<Stmt> statements;
        final long knownCost;
        final long estCost;
        final long totalCost;

        MethodFlow(List<Stmt> stmts, CostSnapshot cost) {
            this.statements = new ArrayList<>(stmts);
            this.knownCost = cost.known;
            this.estCost = cost.estimated;
            this.totalCost = cost.total();
        }

        MethodTraceSet.Trace toOutputFormat(String methodSig, String risk, int id) {
            MethodTraceSet.Trace trace = new MethodTraceSet.Trace();
            trace.setId("flow_" + id);
            trace.setPath(statements.stream()
                    .map(stmt -> "line "
                            + (stmt.getPositionInfo().getStmtPosition() != null
                                    ? stmt.getPositionInfo().getStmtPosition().getFirstLine()
                                    : -1)
                            + ": " + stmt)
                    .collect(Collectors.toList()));
            List<String> tags = new ArrayList<>();
            if (risk.equals("GUARANTEED_VIOLATION"))
                tags.add("GUARANTEED_VIOLATION");
            else if (risk.equals("SUSPECTED"))
                tags.add("SUSPECTED");
            trace.setTags(tags);
            trace.setEstimatedCost(totalCost);
            trace.setKnownCost(knownCost);
            trace.setRiskLevel(risk);
            return trace;
        }
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
    // Inner class for IOSpec annotation data
    // ----------------------------------------------------------------------

    private static class IOSpecAnnotation {
        private final long max;
        private final String unit;
        private final boolean sink;
        private final int percentile;
        private final String desc;

        public IOSpecAnnotation(AnnotationUsage annotation) {
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
