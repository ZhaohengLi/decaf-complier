package decaf.frontend.symbol;

import decaf.frontend.scope.Scope;
import decaf.frontend.tree.Pos;
import decaf.frontend.type.Type;
import decaf.frontend.scope.LambdaFormalScope;

/**
 * Symbols.
 * <p>
 * A symbol is created when a definition is identified and type-checked, indicating a class/variable/method is
 * resolved successfully, by {@link decaf.frontend.typecheck.Namer}.
 * <p>
 * Symbols are used in two ways: stored in the symbol table of a scope, and referred by other expressions/statements.
 *
 * @see ClassSymbol
 * @see MethodSymbol
 * @see VarSymbol
 */
public abstract class Symbol implements Comparable<Symbol> {

    public final String name;

    public Type type;


        /**
         * Get string representation of a symbol, excluding the position.
         *
         * @return string representation
         */
        protected abstract String str();

        @Override
        public String toString() {
            return pos + " -> " + str();
        }

        Scope definedIn;

        /**
         * Two symbols are compared by their positions.
         *
         * @param that another symbol
         * @return comparing result
         */
        @Override
        public int compareTo(Symbol that) {
          System.out.println("form symbol");
            return this.pos.compareTo(that.pos);
        }

            public final Pos pos;

            public Scope lambdaFormalScope;

    Symbol(String name, Type type, Pos pos) {
        this.name = name;
        this.type = type;
        this.pos = pos;
    }

    /**
     * In which scope does this symbol define?
     *
     * @return defined-in scope
     */
    public Scope domain() {
        return definedIn;
    }

    public void setLambdaDomain(Scope lambdaFormalScope) {
      System.out.println("form symbol");
        this.lambdaFormalScope = lambdaFormalScope;
    }


    public Scope lambdaDomain() {
        return lambdaFormalScope;
    }


    public void setDomain(Scope scope) {
        this.definedIn = scope;
    }

    public boolean isClassSymbol() {
        return false;
    }

    public boolean isVarSymbol() {
        return false;
    }

    public boolean isMethodSymbol() {
        return false;
    }

    public boolean isLambdaSymbol() {
        return false;
    }


}
