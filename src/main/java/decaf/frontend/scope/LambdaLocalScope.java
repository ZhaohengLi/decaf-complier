package decaf.frontend.scope;

import java.util.ArrayList;
import java.util.List;

import decaf.frontend.symbol.LambdaSymbol;

public class LambdaLocalScope extends Scope {

    public LambdaLocalScope(Scope scope) {
        super(Kind.LAMBDALOCAL);
        assert scope.isLambdaFormalOrLocalScope();
        if(scope.isLambdaFormalScope())
            ((LambdaFormalScope)scope).setNested(this);
        else if(scope.isLambdaLocalScope())
            ((LambdaLocalScope)scope).nested.add(this);
        else
            ((LocalScope)scope).nested.add(this);
    }



    @Override
    public boolean isLambdaLocalScope() {
        return true;
    }

    public List<Scope> nestedLambdaLocalScope() {
        return nested;
    }

    public List<Scope> nested = new ArrayList<>();
}