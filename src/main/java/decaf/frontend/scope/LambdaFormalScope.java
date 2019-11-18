package decaf.frontend.scope;

import decaf.frontend.symbol.LambdaSymbol;

public class LambdaFormalScope extends Scope {

    private LambdaSymbol owner;
    private LocalScope nested;

    public LambdaFormalScope(Scope parent) {
        super(Kind.LAMBDAFORMAL);
        assert parent.isLocalScope();
        ((LocalScope) parent).nested.add(this);
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

    public LocalScope nestedLocalScope() {
        return nested;
    }

    void setNested(LocalScope scope) {
        nested = scope;
    }
}
