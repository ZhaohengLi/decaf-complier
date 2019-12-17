package decaf.frontend.scope;

import decaf.frontend.symbol.LambdaSymbol;

public class LambdaFormalScope extends Scope {

    private LambdaSymbol owner;
    public Scope nested;

    public LambdaFormalScope(Scope scope) {
        super(Kind.LAMBDAFORMAL);
        if(scope.isLambdaFormalScope())
            ((LambdaFormalScope)scope).setNested(this);
        else if(scope.isLambdaLocalScope())
            ((LambdaLocalScope)scope).nested.add(this);
        else
            ((LocalScope)scope).nested.add(this);
    }

    public LambdaSymbol getOwner() {
        return owner;
    }

    public void setOwner(LambdaSymbol owner) {
        this.owner = owner;
    }

    @Override
    public boolean isLambdaFormalScope() {
        return true;
    }

    public Scope nestedLambdaLocalScope() {
        return nested;
    }

    public void setNested(Scope scope) {
        nested = scope;
    }
}