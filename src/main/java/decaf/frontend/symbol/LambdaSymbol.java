package decaf.frontend.symbol;

import decaf.frontend.scope.ClassScope;
import decaf.frontend.scope.LambdaFormalScope;
import decaf.frontend.scope.Scope;
import decaf.frontend.tree.Pos;
import decaf.frontend.type.FunType;
import decaf.frontend.type.Type;

public final class LambdaSymbol extends Symbol {



    @Override
    public boolean isLambdaSymbol() {
        return true;
    }

    public final FunType funtype;
    public final LambdaFormalScope lambdaFormalScope;
    public Scope inScope;



    @Override
    public ClassScope domain() {
      System.out.println("form symbol");
        return (ClassScope) definedIn;
    }

    @Override
    protected String str() {
      System.out.println("form symbol");
        return String.format("function %s : %s", name, funtype);
    }

    public LambdaSymbol(String name, FunType funtype, Pos pos, LambdaFormalScope lambdaFormalScope) {
        super(name, funtype, pos);
        this.funtype = funtype;
        this.lambdaFormalScope = lambdaFormalScope;
        lambdaFormalScope.setOwner(this);
    }
}
