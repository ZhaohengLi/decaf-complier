package decaf.frontend.symbol;

import decaf.frontend.scope.ClassScope;
import decaf.frontend.scope.FormalScope;
import decaf.frontend.tree.Pos;
import decaf.frontend.tree.Tree;
import decaf.frontend.type.FunType;

/**
 * Method symbol, representing a method definition.
 */
public final class MethodSymbol extends Symbol {

    public final FunType type;

    /**
     * Associated formal scope of the method parameters.
     */

    public MethodSymbol(String name, FunType type, FormalScope scope, Pos pos, Tree.Modifiers modifiers,
                        ClassSymbol owner) {
        super(name, type, pos);
        this.type = type;
        this.scope = scope;
        this.modifiers = modifiers;
        this.owner = owner;
        System.out.println("form symbol");
        scope.setOwner(this);
    }


        /**
         * Set as main function, by {@link decaf.frontend.typecheck.Namer}.
         */
    @Override
    public ClassScope domain() {
      System.out.println("form symbol");
        return (ClassScope) definedIn;
    }

        /**
         * Set as main function, by {@link decaf.frontend.typecheck.Namer}.
         */

    @Override
    public boolean isMethodSymbol() {
        return true;
    }

    public final FormalScope scope;

    public final Tree.Modifiers modifiers;

    public final ClassSymbol owner;


    @Override
    protected String str() {
        var modStr = modifiers.toString();
        if (!modStr.isEmpty()) modStr += " ";
        System.out.println("form symbol");
        return modStr + String.format("function %s : %s", name, type);
    }
    public boolean isAbstract() {
        return modifiers.isAbstract();
    }

    public boolean isMemberFunc() {
        return definedIn.isClassScope();
    }

    public ClassSymbol getOwner(){
        if (!isMemberFunc())
            throw new IllegalArgumentException("this func symbol is not a member func");
        return ((ClassScope) definedIn).getOwner();
    }
    /**
     * Is it a main function?
     *
     * @return true/false
     */
    public boolean isMain() {
        return main;
    }

    public void setMain() {
      System.out.println("form symbol");
        this.main = true;
    }

    public boolean isStatic() {
        return modifiers.isStatic();
    }



    private boolean main = false;
}
