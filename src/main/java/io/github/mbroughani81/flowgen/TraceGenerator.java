package io.github.mbroughani81.flowgen;

import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.OTFCompileAnalysisInputLocation;
import sootup.core.graph.ControlFlowGraph;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.jimple.common.stmt.JInvokeStmt;
import sootup.core.jimple.common.stmt.JReturnStmt;
import sootup.core.jimple.common.stmt.JThrowStmt;
import sootup.core.model.*;
import sootup.java.core.*;
import sootup.java.core.views.JavaView;
import sootup.java.core.types.JavaClassType;
import sootup.core.jimple.common.constant.LongConstant;
import sootup.core.jimple.common.constant.StringConstant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.*;
import java.util.*;

public class TraceGenerator {

    private final JavaView view;
    private final Gson gson;

    public TraceGenerator(String classpathDirectory) {
        // Initialize SootUp with the classpath
        this.view = createView(classpathDirectory);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    private JavaView createView(String classpathDir) {
        // Create analysis input location
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
            System.out.println("CCC");
            return results;
        }

        JavaSootClass sootClass = view.getClass(classType).get();
        System.out.println("OKK");

        // START LOG
        for (JavaSootMethod method : sootClass.getMethods()) {
            // Debug method-level annotations
            System.out.println("Method: " + method.getName());
            for (AnnotationUsage annotation : method.getAnnotations()) {
                System.out.println("  Annotation: " + annotation.getAnnotation().getClassName());
            }
        }
        // END LOG

        // Iterate through all methods
        for (JavaSootMethod method : sootClass.getMethods()) {
            // Check if method has @IOSpec annotation
            Optional<IOSpecAnnotation> iospecOpt = extractIOSpecAnnotation(method);

            if (iospecOpt.isPresent()) {
                System.out.println(method.toString());
                IOSpecAnnotation iospec = iospecOpt.get();

                // Check if method has a body (not abstract/native)
                if (method.hasBody()) {
                    Body body = method.getBody();
                    MethodTraceSet set = analyzeMethod(method, body, iospec);
                    results.add(set);
                }
            }
        }
        return results;
    }

    /**
     * Extract @IOSpec annotation from method
     */
    private Optional<IOSpecAnnotation> extractIOSpecAnnotation(JavaSootMethod method) {
        // In SootUp, check for annotation tags
        for (AnnotationUsage annotation : method.getAnnotations()) {
            if (annotation.getAnnotation().getFullyQualifiedName().equals("io.github.mbroughani81.perfspec.IOSpec")) {
                return Optional.of(new IOSpecAnnotation(annotation));
            }
        }
        return Optional.empty();
    }

    /**
     * Analyze a single annotated method
     */
    private MethodTraceSet analyzeMethod(SootMethod method, Body body, IOSpecAnnotation iospec) {
        // Build Control Flow Graph
        ControlFlowGraph<?> cfg = method.getBody().getControlFlowGraph();

        // Create spec info
        MethodTraceSet.SpecInfo specInfo = new MethodTraceSet.SpecInfo();
        specInfo.setType("IO");
        specInfo.setMax(iospec.getMax());
        specInfo.setUnit(iospec.getUnit());
        specInfo.setPercentile(iospec.getPercentile());

        // Extract flows
        List<MethodTraceSet.Trace> traces = extractTraces(method, body, cfg, iospec);

        // Build result
        MethodTraceSet set = new MethodTraceSet();
        set.setMethod(method.getSignature().toString());
        set.setSpec(specInfo);
        set.setTraces(traces);

        return set;
    }

    /**
     * Extract all feasible flows from the method
     */
    private List<MethodTraceSet.Trace> extractTraces(
            SootMethod method,
            Body body,
            ControlFlowGraph<?> cfg,
            IOSpecAnnotation iospec) {

        List<MethodTraceSet.Trace> traces = new ArrayList<>();

        // 1. Find all paths (control-flow sensitivity)
        List<List<Stmt>> allPaths = findAllControlFlowPaths(cfg, body);

        // 2. Apply loop-awareness and generate variants
        int traceId = 1;
        for (List<Stmt> path : allPaths) {
            MethodTraceSet.Trace trace = convertPathToTrace(path, method, traceId++, iospec);
            traces.add(trace);
        }

        return traces;
    }

    /**
     * Find all control-flow paths (simplified - for production, use proper path
     * enumeration)
     */
    private List<List<Stmt>> findAllControlFlowPaths(
            ControlFlowGraph<?> cfg,
            Body body) {
        List<List<Stmt>> allPaths = new ArrayList<>();

        Stmt firstStmt = body.getStmts().get(0);

        Set<Stmt> exitStmts = findExitPoints(body, cfg);

        for (Stmt exitStmt : exitStmts) {
            List<Stmt> currentPath = new ArrayList<>();
            Set<Stmt> visited = new HashSet<>();
            findAllPathsDFS(cfg,
                    firstStmt, exitStmt, currentPath, visited, allPaths);
        }

        return allPaths;
    }

    /**
     * Find exit points (return statements, throw statements)
     */
    private Set<Stmt> findExitPoints(Body body, ControlFlowGraph<?> cfg) {
        Set<Stmt> exits = new HashSet<>();
        for (Stmt stmt : body.getStmts()) {
            // Check if it's an invoke statement
            if (stmt instanceof JInvokeStmt) {
                // Check if it's actually an exit point by looking at successors
                if (cfg.outDegree(stmt) == 0) {
                    exits.add(stmt);
                }
            }
            // Check for return statements
            else if (stmt instanceof JReturnStmt) {
                exits.add(stmt); // Returns are always exit points
            }
            // Check for throw statements
            else if (stmt instanceof JThrowStmt) {
                exits.add(stmt); // Throws are always exit points if uncaught
            }
        }
        return exits;
    }

    private void findAllPathsDFS(ControlFlowGraph<?> cfg,
            Stmt current, Stmt target,
            List<Stmt> currentPath,
            Set<Stmt> visited,
            List<List<Stmt>> allPaths) {
        currentPath.add(current);
        visited.add(current);

        if (current.equals(target)) {
            allPaths.add(new ArrayList<>(currentPath));
        } else {
            for (Stmt successor : cfg.successors(current)) {
                if (!visited.contains(successor)) {
                    findAllPathsDFS(cfg, successor, target,
                                    currentPath, visited, allPaths);
                }
            }
        }

        currentPath.remove(currentPath.size() - 1);
        visited.remove(current);
    }

    /**
     * Convert a sequence of statements to a Flow object
     */
    private MethodTraceSet.Trace convertPathToTrace(List<Stmt> path, SootMethod method,
            int flowId, IOSpecAnnotation iospec) {
        MethodTraceSet.Trace trace = new MethodTraceSet.Trace();
        trace.setId("flow_" + flowId);

        List<String> pathDescriptions = new ArrayList<>();
        List<String> tags = new ArrayList<>();

        boolean suspectLoop = false;

        for (int i = 0; i < path.size(); i++) {
            Stmt stmt = path.get(i);
            String description = buildStatementDescription(stmt, method);
            pathDescriptions.add(description);

            // Check for loops (simplified heuristic)
            if (stmt.toString().contains("while") || stmt.toString().contains("for")) {
                suspectLoop = true;

                // Check if loop body contains I/O operations
                if (containsIOOperation(stmt)) {
                    tags.add("PERF_SUSPECT_LOOP");
                }
            }
        }

        if (suspectLoop) {
            tags.add("LOOP_PRESENT");
        }

        trace.setPath(pathDescriptions);
        trace.setTags(tags);

        return trace;
    }

    /**
     * Build a human-readable description of a statement
     */
    private String buildStatementDescription(Stmt stmt, SootMethod method) {
        Position position = stmt.getPositionInfo().getStmtPosition();
        int lineNumber = position != null ? position.getFirstLine() : -1;
        return "line " + lineNumber + ": " +
                stmt.toString();
    }

    /**
     * Check if a statement contains I/O operations
     */
    private boolean containsIOOperation(Stmt stmt) {
        String stmtStr = stmt.toString().toLowerCase();
        return stmtStr.contains("io") || stmtStr.contains("read") ||
                stmtStr.contains("write") || stmtStr.contains("network") ||
                stmtStr.contains("database") || stmtStr.contains("file");
    }

    /**
     * Export results to JSON
     */
    public String exportToJson(List<MethodTraceSet> traces) {
        return gson.toJson(traces);
    }

    // Inner class to hold annotation data
    private static class IOSpecAnnotation {
        private final long max;
        private final String unit;
        private final int percentile;
        private final String desc;

        public IOSpecAnnotation(AnnotationUsage annotation) {
            // Extract values from annotation with proper type handling
            this.max = getLongValue(annotation, "max", 100L);
            this.unit = getStringValue(annotation, "unit", "ms");
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

        public long getMax() {
            return max;
        }

        public String getUnit() {
            return unit;
        }

        public int getPercentile() {
            return percentile;
        }

        public String getDesc() {
            return desc;
        }
    }
}
