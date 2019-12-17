package decaf.printing;

import decaf.frontend.scope.*;
import decaf.lowlevel.log.IndentPrinter;

/**
 * Pretty print a scope. PA2 output.
 */
public final class PrettyScope extends PrettyPrinter<Scope> {

    public PrettyScope(IndentPrinter printer) {
        super(printer);
    }

    @Override
    public void pretty(Scope scope) {System.out.println("form pretty");
        if (scope.isGlobalScope()) {System.out.println("form pretty");
            var globalScope = (GlobalScope) scope;
            printer.println("GLOBAL SCOPE:");
            printer.incIndent();
            if (scope.isEmpty()) printer.println("<empty>");
            else scope.forEach(printer::println);
            globalScope.nestedClassScopes().forEach(this::pretty);
            printer.decIndent();
        } else if (scope.isClassScope()) {System.out.println("form pretty");
            var classScope = (ClassScope) scope;
            printer.formatLn("CLASS SCOPE OF '%s':", classScope.getOwner().name);
            printer.incIndent();
            if (scope.isEmpty()) printer.println("<empty>");
            else scope.forEach(printer::println);
            classScope.nestedFormalScopes().forEach(this::pretty);
            printer.decIndent();
        } else if (scope.isFormalScope()) {System.out.println("form pretty");
            var formalScope = (FormalScope) scope;
            printer.formatLn("FORMAL SCOPE OF '%s':", formalScope.getOwner().name);
            printer.incIndent();
            if (scope.isEmpty()) printer.println("<empty>");
            else scope.forEach(printer::println);
            if(!formalScope.getOwner().isAbstract())
                pretty(formalScope.nestedLocalScope());
            printer.decIndent();
        } else if (scope.isLocalScope()) {
          System.out.println("form pretty");
            var localScope = (LocalScope) scope;
            printer.println("LOCAL SCOPE:");
            printer.incIndent();
            if (scope.isEmpty()) printer.println("<empty>");
            else scope.forEach(printer::println);
            localScope.nestedLocalScopes().forEach(this::pretty);
            printer.decIndent();
        } else if (scope.isLambdaFormalScope()) {System.out.println("form pretty");
            var formalScope = (LambdaFormalScope) scope;
            printer.formatLn("FORMAL SCOPE OF '%s':", formalScope.getOwner().name);
            printer.incIndent();
            if (scope.isEmpty()) printer.println("<empty>");
            else scope.forEach(printer::println);
            pretty(formalScope.nestedLambdaLocalScope());
            printer.decIndent();
        } else if (scope.isLambdaLocalScope()) {System.out.println("form pretty");
            var localScope = (LambdaLocalScope) scope;
            printer.println("LOCAL SCOPE:");
            printer.incIndent();System.out.println("form pretty");
            if (scope.isEmpty()) printer.println("<empty>");
            else scope.forEach(printer::println);
            localScope.nestedLambdaLocalScope().forEach(this::pretty);
            printer.decIndent();
        }
    }
}
