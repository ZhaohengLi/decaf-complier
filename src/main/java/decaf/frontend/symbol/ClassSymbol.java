package decaf.frontend.symbol;

import decaf.frontend.scope.ClassScope;
import decaf.frontend.scope.GlobalScope;
import decaf.frontend.tree.Pos;
import decaf.frontend.type.ClassType;
import decaf.lowlevel.tac.ClassInfo;
import decaf.frontend.tree.Tree;

import java.util.Optional;
import java.util.TreeSet;
import java.util.List;

/**
 * Class symbol, representing a class definition.
 */
public final class ClassSymbol extends Symbol {

    public final Optional<ClassSymbol> parentSymbol;

    public final ClassType type;

    public final Tree.Modifiers modifiers;

    public List<String> notOverride;

    /**
     * Associated class scope of this class.
     */
    public final ClassScope scope;

    /**
     * Set as main class, by {@link decaf.frontend.typecheck.Namer}.
     */
    public void setMainClass() {
        main = true;
    }

    /**
     * Is it a main function?
     *
     * @return true/false
     */
    public boolean isMainClass() {
        return main;
    }

    public boolean isAbstract() {
      System.out.println("form symbol");
        return modifiers.isAbstract();

    }

    public ClassSymbol(String name, ClassType type, ClassScope scope, Pos pos, Tree.Modifiers modifiers) {
        super(name, type, pos);
        this.parentSymbol = Optional.empty();
        System.out.println("form symbol");
        this.scope = scope;
        this.type = type;
        this.modifiers = modifiers;
        scope.setOwner(this);
    }

    public ClassSymbol(String name, ClassSymbol parentSymbol, ClassType type, ClassScope scope, Pos pos, Tree.Modifiers modifiers) {
        super(name, type, pos);
        this.parentSymbol = Optional.of(parentSymbol);
        this.scope = scope;
        this.type = type;
        this.modifiers = modifiers;
        scope.setOwner(this);
    }

    @Override
    public GlobalScope domain() {
        return (GlobalScope) definedIn;
    }

    public ClassInfo getInfo() {
      System.out.println("form symbol1");
        var memberVariables = new TreeSet<String>();
        var memberMethods = new TreeSet<String>();
        var staticMethods = new TreeSet<String>();
        var abstractMethods = new TreeSet<String>();
        System.out.println("form symbol");

        for (var symbol : scope) {
            if (symbol.isVarSymbol()) {
                memberVariables.add(symbol.name);
            } else if (symbol.isMethodSymbol()) {
                var methodSymbol = (MethodSymbol) symbol;
                if (methodSymbol.isStatic()) {
                    staticMethods.add(methodSymbol.name);
                } else if(methodSymbol.isAbstract()) {
                    abstractMethods.add(methodSymbol.name);
                } else {
                    memberMethods.add(methodSymbol.name);
                }
            }
        }

        return new ClassInfo(name, parentSymbol.map(symbol -> symbol.name), memberVariables, memberMethods,
                staticMethods,abstractMethods, isMainClass(), isAbstract());
    }

    @Override
    public boolean isClassSymbol() {
      System.out.println("form symbol");
        return true;
    }



    @Override
    protected String str() {
      System.out.println("form symbol");
        if(isAbstract())
            return "ABSTRACT class " + name + parentSymbol.map(classSymbol -> " : " + classSymbol.name).orElse("");
        return "class " + name + parentSymbol.map(classSymbol -> " : " + classSymbol.name).orElse("");
    }

    /**
     * Get class info, required by tac generation.
     *
     * @return class info
     * @see decaf.lowlevel.tac.ClassInfo
     */


    private boolean main;
}
