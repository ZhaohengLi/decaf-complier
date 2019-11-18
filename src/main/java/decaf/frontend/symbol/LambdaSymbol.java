package decaf.frontend.symbol;

import decaf.frontend.scope.*;
import decaf.frontend.tree.*;
import decaf.frontend.type.*;

public final class LambdaSymbol extends Symbol {

    public final FunType type;
    public final LambdaFormalScope formalScope;

    public LambdaSymbol(String name, FunType type, Pos pos, LambdaFormalScope formalScope){
        super(name, type, pos);
        this.type = type;
        this.formalScope = formalScope;
        formalScope.setOwner(this);
    }

    @Override
    public boolean isLambdaSymbol() {
        return true;
    }

    @Override
    protected String str() {
        String str = String.format("function %s : %s", name, type);
        System.out.println(str);
        return str;
    }
}
