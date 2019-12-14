package decaf.frontend.typecheck;

import decaf.driver.Config;
import decaf.driver.Phase;
import decaf.driver.error.*;
import decaf.frontend.scope.*;
import decaf.frontend.symbol.*;
import decaf.frontend.tree.Pos;
import decaf.frontend.tree.Tree;
import decaf.frontend.type.*;
import decaf.lowlevel.log.IndentPrinter;
import decaf.printing.PrettyScope;

import java.util.*;

/**
 * The typer phase: type check abstract syntax tree and annotate nodes with inferred (and checked) types.
 */
public class Typer extends Phase<Tree.TopLevel, Tree.TopLevel> implements TypeLitVisited {

    public Typer(Config config) {
        super("typer", config);
    }

    @Override
    public Tree.TopLevel transform(Tree.TopLevel tree) {
        var ctx = new ScopeStack(tree.globalScope);
        tree.accept(this, ctx);
        return tree;
    }

    @Override
    public void onSucceed(Tree.TopLevel tree) {
        if (config.target.equals(Config.Target.PA2)) {
            var printer = new PrettyScope(new IndentPrinter(config.output));
            printer.pretty(tree.globalScope);
            printer.flush();
        }
    }

    @Override
    public void visitTopLevel(Tree.TopLevel program, ScopeStack ctx) {
        //System.out.println("Typer visitTopLevel");
        for (var clazz : program.classes) {
            clazz.accept(this, ctx);
        }
    }

    @Override
    public void visitClassDef(Tree.ClassDef clazz, ScopeStack ctx) {
        //System.out.println("Typer visitClassDef "+clazz.name);
        ctx.open(clazz.symbol.scope);
        for (var field : clazz.fields) {
            field.accept(this, ctx);
        }
        ctx.close();
    }

    @Override
    public void visitMethodDef(Tree.MethodDef method, ScopeStack ctx) {
        //System.out.println("Typer visitMethodDef "+method.name);
        if (!method.isAbstract()) {
            ctx.open(method.symbol.scope);
            method.body.accept(this, ctx);
            if (!method.symbol.type.returnType.isVoidType() && !method.body.returns) {
                issue(new MissingReturnError(method.body.pos));
            }
            ctx.close();
        }
    }

    /**
     * To determine if a break statement is legal or not, we need to know if we are inside a loop, i.e.
     * loopLevel {@literal >} 1?
     * <p>
     * Increase this counter when entering a loop, and decrease it when leaving a loop.
     */
    private int loopLevel = 0;

    @Override
    public void visitBlock(Tree.Block block, ScopeStack ctx) {
        //System.out.println("Typer visitBlock");
        ctx.open(block.scope);
        for (var stmt : block.stmts) {
            stmt.accept(this, ctx);
            if (stmt.isClosed) block.isClosed = true;
        }
        ctx.close();
        block.returns = !block.stmts.isEmpty() && block.stmts.get(block.stmts.size() - 1).returns;
    }

    @Override
    public void visitAssign(Tree.Assign stmt, ScopeStack ctx) {
        //System.out.println("Typer visitAssign");
        stmt.lhs.accept(this, ctx);
        stmt.rhs.accept(this, ctx);
        var lt = stmt.lhs.type;
        var rt = stmt.rhs.type;

        if (!suitableForLambdaAssign(stmt.lhs, ctx)) {
            issue(new MyLambdaError1(stmt.pos));
            //System.out.println("Typer visitAssign - MyLambdaError1 " + stmt.pos);
        }

        if (lt.noError() && !rt.subtypeOf(lt)) {
            issue(new IncompatBinOpError(stmt.pos, lt.toString(), "=", rt.toString()));
            //System.out.println("Typer visitAssign - IncompatBinOpError " + stmt.pos);
        }

        if (lt.noError() && (stmt.lhs instanceof Tree.VarSel) && ((Tree.VarSel)(stmt.lhs)).isMethod) {
            issue(new MyFunAssignError1(stmt.pos, ((Tree.VarSel)(stmt.lhs)).name));

        }
    }

    private boolean suitableForLambdaAssign(Tree.LValue lValue, ScopeStack ctx) {
        boolean suitable = true;

        boolean isAssignedInLambda = ctx.currentLambda() != null;
        boolean isVarSel = lValue instanceof Tree.VarSel;

        if (lValue.type.noError() && isAssignedInLambda && isVarSel) {
            boolean isInCurrentLambdaFormalScope = ctx.containsInCurrentLambdaFormalScope(((Tree.VarSel)lValue).symbol.name);
            boolean isInClassScope = ((Tree.VarSel)lValue).symbol.domain().isClassScope();
            if (!isInCurrentLambdaFormalScope && ! isInClassScope) suitable = false;
        }

        return suitable;
    }

    @Override
    public void visitExprEval(Tree.ExprEval stmt, ScopeStack ctx) {
        //System.out.println("Typer visitExprEval");
        stmt.expr.accept(this, ctx);
    }


    @Override
    public void visitIf(Tree.If stmt, ScopeStack ctx) {
        //System.out.println("Typer visitIf");
        checkTestExpr(stmt.cond, ctx);
        stmt.trueBranch.accept(this, ctx);
        stmt.falseBranch.ifPresent(b -> b.accept(this, ctx));
        // if-stmt returns a value iff both branches return
        stmt.returns = stmt.trueBranch.returns && stmt.falseBranch.isPresent() && stmt.falseBranch.get().returns;
        stmt.isClosed = stmt.trueBranch.isClosed && stmt.falseBranch.isPresent() && stmt.falseBranch.get().isClosed;
    }

    @Override
    public void visitWhile(Tree.While loop, ScopeStack ctx) {
        //System.out.println("Typer visitWhile");
        checkTestExpr(loop.cond, ctx);
        loopLevel++;
        loop.body.accept(this, ctx);
        loopLevel--;
    }

    @Override
    public void visitFor(Tree.For loop, ScopeStack ctx) {
        //System.out.println("Typer visitFor");
        ctx.open(loop.scope);
        loop.init.accept(this, ctx);
        checkTestExpr(loop.cond, ctx);
        loop.update.accept(this, ctx);
        loopLevel++;
        for (var stmt : loop.body.stmts) {
            stmt.accept(this, ctx);
        }
        loopLevel--;
        ctx.close();
    }

    @Override
    public void visitBreak(Tree.Break stmt, ScopeStack ctx) {
        //System.out.println("Typer visitBreak");
        if (loopLevel == 0) {
            issue(new BreakOutOfLoopError(stmt.pos));
        }
    }

    @Override
    public void visitReturn(Tree.Return stmt, ScopeStack ctx) {
        //System.out.println("Typer visitReturn");
        if (ctx.currentLambda() == null){
            var expected = ctx.currentMethod().type.returnType;
            stmt.expr.ifPresent(e -> e.accept(this, ctx));
            var actual = stmt.expr.map(e -> e.type).orElse(BuiltInType.VOID);
            if (actual.noError() && !actual.subtypeOf(expected)) {
                //System.out.println("Typer visitReturn - BadReturnTypeError");
                issue(new BadReturnTypeError(stmt.pos, expected.toString(), actual.toString()));
            }
            stmt.returns = stmt.expr.isPresent();// boolean 标志位
        } else {
            //System.out.println("Typer visitReturn - lambda");
            stmt.expr.ifPresent(e -> e.accept(this, ctx));
            var actual = stmt.expr.map(e -> e.type).orElse(BuiltInType.VOID);
            stmt.isClosed = true;
            ctx.currentLambdaReturnTypeList().add(actual);
            stmt.returns = stmt.expr.isPresent();// boolean 标志位
        }

    }

    @Override
    public void visitPrint(Tree.Print stmt, ScopeStack ctx) {
        //System.out.println("Typer visitPrint");
        int i = 0;
        for (var expr : stmt.exprs) {
            expr.accept(this, ctx);
            i++;
            if (expr.type.noError() && !expr.type.isBaseType()) {
                issue(new BadPrintArgError(expr.pos, Integer.toString(i), expr.type.toString()));
            }
        }
    }

    private void checkTestExpr(Tree.Expr expr, ScopeStack ctx) {
        //System.out.println("Typer checkTestExpr");
        expr.accept(this, ctx);
        if (expr.type.noError() && !expr.type.eq(BuiltInType.BOOL)) {
            issue(new BadTestExpr(expr.pos));
        }
    }

    // Expressions

    @Override
    public void visitIntLit(Tree.IntLit that, ScopeStack ctx) {
        that.type = BuiltInType.INT;
    }

    @Override
    public void visitBoolLit(Tree.BoolLit that, ScopeStack ctx) {
        that.type = BuiltInType.BOOL;
    }

    @Override
    public void visitStringLit(Tree.StringLit that, ScopeStack ctx) {
        that.type = BuiltInType.STRING;
    }

    @Override
    public void visitNullLit(Tree.NullLit that, ScopeStack ctx) {
        that.type = BuiltInType.NULL;
    }

    @Override
    public void visitReadInt(Tree.ReadInt readInt, ScopeStack ctx) {
        readInt.type = BuiltInType.INT;
    }

    @Override
    public void visitReadLine(Tree.ReadLine readStringExpr, ScopeStack ctx) {
        readStringExpr.type = BuiltInType.STRING;
    }

    @Override
    public void visitUnary(Tree.Unary expr, ScopeStack ctx) {
        expr.operand.accept(this, ctx);
        var t = expr.operand.type;
        if (t.noError() && !compatible(expr.op, t)) {
            // Only report this error when the operand has no error, to avoid nested errors flushing.
            issue(new IncompatUnOpError(expr.pos, Tree.opStr(expr.op), t.toString()));
        }

        // Even when it doesn't type check, we could make a fair guess based on the operator kind.
        // Let's say the operator is `-`, then one possibly wants an integer as the operand.
        // Once he/she fixes the operand, according to our type inference rule, the whole unary expression
        // must have type int! Thus, we simply _assume_ it has type int, rather than `NoType`.
        expr.type = resultTypeOf(expr.op);
    }

    public boolean compatible(Tree.UnaryOp op, Type operand) {
        //System.out.println("Typer compatible");
        return switch (op) {
            case NEG -> operand.eq(BuiltInType.INT); // if e : int, then -e : int
            case NOT -> operand.eq(BuiltInType.BOOL); // if e : bool, then !e : bool
        };
    }

    public Type resultTypeOf(Tree.UnaryOp op) {
        //System.out.println("resultTypeOf");
        return switch (op) {
            case NEG -> BuiltInType.INT;
            case NOT -> BuiltInType.BOOL;
        };
    }

    @Override
    public void visitBinary(Tree.Binary expr, ScopeStack ctx) {
        //System.out.println("Typer visitBinary");
        expr.lhs.accept(this, ctx);
        expr.rhs.accept(this, ctx);
        var t1 = expr.lhs.type;
        var t2 = expr.rhs.type;
        if (t1.noError() && t2.noError() && !compatible(expr.op, t1, t2)) {
            //System.out.println("IncompatBinOpError");
            issue(new IncompatBinOpError(expr.pos, t1.toString(), Tree.opStr(expr.op), t2.toString()));
        }
        expr.type = resultTypeOf(expr.op);
    }

    public boolean compatible(Tree.BinaryOp op, Type lhs, Type rhs) {
        //System.out.println("Typer compatible");
        if (op.compareTo(Tree.BinaryOp.ADD) >= 0 && op.compareTo(Tree.BinaryOp.MOD) <= 0) { // arith
            // if e1, e2 : int, then e1 + e2 : int
            return lhs.eq(BuiltInType.INT) && rhs.eq(BuiltInType.INT);
        }

        if (op.equals(Tree.BinaryOp.AND) || op.equals(Tree.BinaryOp.OR)) { // logic
            // if e1, e2 : bool, then e1 && e2 : bool
            return lhs.eq(BuiltInType.BOOL) && rhs.eq(BuiltInType.BOOL);
        }

        if (op.equals(Tree.BinaryOp.EQ) || op.equals(Tree.BinaryOp.NE)) { // eq
            // if e1 : T1, e2 : T2, T1 <: T2 or T2 <: T1, then e1 == e2 : bool
            return lhs.subtypeOf(rhs) || rhs.subtypeOf(lhs);
        }

        // compare
        // if e1, e2 : int, then e1 > e2 : bool
        return lhs.eq(BuiltInType.INT) && rhs.eq(BuiltInType.INT);
    }

    public Type resultTypeOf(Tree.BinaryOp op) {
        //System.out.println("Typer resultTypeOf");
        if (op.compareTo(Tree.BinaryOp.ADD) >= 0 && op.compareTo(Tree.BinaryOp.MOD) <= 0) { // arith
            return BuiltInType.INT;
        }
        return BuiltInType.BOOL;
    }

    @Override
    public void visitNewArray(Tree.NewArray expr, ScopeStack ctx) {
        //System.out.println("Typer visitNewArray");
        expr.elemType.accept(this, ctx);
        expr.length.accept(this, ctx);
        var et = expr.elemType.type;
        var lt = expr.length.type;

        if (et.isVoidType()) {
            issue(new BadArrElementError(expr.elemType.pos));
            expr.type = BuiltInType.ERROR;
        } else {
            expr.type = new ArrayType(et);
        }

        if (lt.noError() && !lt.eq(BuiltInType.INT)) {
            issue(new BadNewArrayLength(expr.length.pos));
        }
    }

    @Override
    public void visitNewClass(Tree.NewClass expr, ScopeStack ctx) {
        //System.out.println("Typer visitNewClass");
        var clazz = ctx.lookupClass(expr.clazz.name);
        if (clazz.isPresent()) {
            expr.symbol = clazz.get();
            if (expr.symbol.isAbstract()){
                issue(new MyAbstractError2(expr.pos, expr.clazz.name));
                expr.type = BuiltInType.ERROR;
            } else {
                expr.type = expr.symbol.type;
            }
        } else {
            issue(new ClassNotFoundError(expr.pos, expr.clazz.name));
            expr.type = BuiltInType.ERROR;
        }
    }

    @Override
    public void visitThis(Tree.This expr, ScopeStack ctx) {
        if (ctx.currentMethod().isStatic()) {
            issue(new ThisInStaticFuncError(expr.pos));
        }
        expr.type = ctx.currentClass().type;
    }

    private boolean allowClassNameVar = false;

    @Override
    public void visitVarSel(Tree.VarSel expr, ScopeStack ctx) {
        System.out.println("Typer visitVarSel "+expr.pos);
        if (expr.receiver.isEmpty()) {
            //System.out.println("Typer visitVarSel - has no receiver");
            // Variable, which should be complicated since a legal variable could refer to a local var,
            // a visible member var, and a class name.
            var symbol = ctx.lookupBefore(expr.name, localVarDefPos.orElse(expr.pos));
            if (symbol.isPresent()) {
                //System.out.println("Typer visitVarSel - symbol.isPresent()");
                if (symbol.get().isVarSymbol()) {
                    //System.out.println("Typer visitVarSel - isVarSymbol()");
                    var var = (VarSymbol) symbol.get();
                    expr.symbol = var;
                    if (var.type.eq(BuiltInType.WAIT)) {
                        issue(new UndeclVarError(expr.pos, expr.name));
                        //System.out.println("Typer visitVarSel - UndeclVarError " + expr.pos);
                        expr.type = BuiltInType.ERROR;
                        return;
                    }
                    expr.type = var.type;
                    //System.out.println("Typer visitVarSel - expr.symbol is "+expr.symbol);
                    //System.out.println("Typer visitVarSel - expr.type is "+expr.type);
                    if (var.isMemberVar()) {
                        //System.out.println("Typer visitVarSel - var.isMemberVar()");
                        if (ctx.currentMethod().isStatic()) {
                            issue(new RefNonStaticError(expr.pos, ctx.currentMethod().name, expr.name));
                        } else {
                            expr.setThis();
                        }
                    }
                    return;
                }

                if (symbol.get().isClassSymbol() && allowClassNameVar) { // special case: a class name
                    //System.out.println("Typer visitVarSel - isClassSymbol()");
                    var clazz = (ClassSymbol) symbol.get();
                    expr.type = clazz.type;
                    expr.isClassName = true;
                    return;
                }

                if (symbol.get().isMethodSymbol()) {
                    expr.MethodSymbol = (MethodSymbol) symbol.get();
                    expr.isMethod = true;
                    expr.type = ((MethodSymbol)symbol.get()).type;
                    if (ctx.currentMethod().isStatic() && !((MethodSymbol)symbol.get()).isStatic()) {
                        issue(new RefNonStaticError(expr.pos, ctx.currentMethod().name, expr.name));
                    } else {
                        expr.setThis();
                    }
                    return;
                }
            }
            //System.out.println("Typer visitVarSel - !symbol.isPresent()");
            expr.type = BuiltInType.ERROR;
            issue(new UndeclVarError(expr.pos, expr.name));
            return;
        }

        // has receiver
        //System.out.println("Typer visitVarSel - has receiver");
        var receiver = expr.receiver.get();
        allowClassNameVar = true;
        receiver.accept(this, ctx);
        allowClassNameVar = false;
        var rt = receiver.type;
        expr.type = BuiltInType.ERROR;

        if (rt.isArrayType() && expr.name.equals("length")) {
            expr.type = new FunType(BuiltInType.INT, new ArrayList<Type>());
            expr.isArrayLength = true;
            return;
        }

        if (!rt.noError()) {
            return;
        }

        if (!rt.isClassType()) {
            issue(new NotClassFieldError(expr.pos, expr.name, rt.toString()));
            return;
        }

        var ct = (ClassType) rt;
        var field = ctx.getClass(ct.name).scope.lookup(expr.name);

        if (receiver instanceof Tree.VarSel) {
            var v1 = (Tree.VarSel) receiver;
            if (v1.isClassName) {
                if (field.isPresent() &&
                    (field.get().isVarSymbol() || (field.get().isMethodSymbol() && !((MethodSymbol)field.get()).isStatic()))) {
                    // special case like MyClass.foo: report error cannot access field 'foo' from 'class : MyClass'
                    issue(new NotClassFieldError(expr.pos, expr.name, ctx.getClass(v1.name).type.toString()));
                    return;
                }
            }
        }

        if (field.isPresent() && field.get().isVarSymbol()) {
            var var = (VarSymbol) field.get();
            if (var.isMemberVar()) {
                expr.symbol = var;
                expr.type = var.type;
                if (!ctx.currentClass().type.subtypeOf(var.getOwner().type)) {
                    // member vars are protected
                    issue(new FieldNotAccessError(expr.pos, expr.name, ct.toString()));
                }
            }
        } else if (field.isPresent() && field.get().isMethodSymbol()) {
            expr.isMethod = true;
            expr.MethodSymbol = (MethodSymbol) field.get();
            expr.type = ((MethodSymbol) field.get()).type;

        } else if (field.isEmpty()) {
            issue(new FieldNotFoundError(expr.pos, expr.name, ct.toString()));
        } else {
            issue(new NotClassFieldError(expr.pos, expr.name, ct.toString()));
        }
    }

    @Override
    public void visitIndexSel(Tree.IndexSel expr, ScopeStack ctx) {
        //System.out.println("Typer visitIndexSel");
        expr.array.accept(this, ctx);
        expr.index.accept(this, ctx);
        var at = expr.array.type;
        var it = expr.index.type;

        if (at.eq(BuiltInType.ERROR)) {
            expr.type = BuiltInType.ERROR;
            return;
        }

        if (!at.isArrayType()) {
            issue(new NotArrayError(expr.array.pos));
            expr.type = BuiltInType.ERROR;
            return;
        }

        expr.type = ((ArrayType) at).elementType;
        if (!it.eq(BuiltInType.INT)) {
            issue(new SubNotIntError(expr.pos));
        }
    }

    @Override
    public void visitCall(Tree.Call expr, ScopeStack ctx) {
        System.out.println("Typer visitCall @"+expr.pos);
        Tree.Expr id =  expr.receiver.get();
        id.accept(this, ctx);
        //System.out.println("Typer visitCall - after accept id");

        if (!id.type.noError()) {
            //System.out.println("Typer visitCall - type has error");
            expr.type = BuiltInType.ERROR;
            return;
        }
        if (!id.type.isFuncType()) {
            //System.out.println("Typer visitCall - type is not functype");
            issue(new MyFunCallError1(expr.pos, id.type.toString()));
            expr.type = BuiltInType.ERROR;
            return;
        }
        if ((id instanceof Tree.VarSel) && ((Tree.VarSel)id).isArrayLength) {
            //System.out.println("Typer visitCall - is array length");
            if (!expr.args.isEmpty()) issue(new BadLengthArgError(expr.pos, expr.args.size()));
            expr.isArrayLength = true;
            expr.type = BuiltInType.INT;
            return;
        }
        typeCall(expr, ctx);
    }

    private void typeCall(Tree.Call expr, ScopeStack ctx) {
        //System.out.println("Typer typeCall");
        for (Tree.Expr arg : expr.args) {
            arg.accept(this, ctx);
        }
        Tree.Expr id =  expr.receiver.get();

        Type expectedReturnType = ((FunType)(id.type)).returnType;
        List<Type> expectedArgTypes = ((FunType)(id.type)).argTypes;
        int expectedArgCount = ((FunType)(id.type)).arity();
        int gotArgCount = expr.args.size();

        expr.type = expectedReturnType;

        if (expectedArgCount != gotArgCount) {
            if (id instanceof Tree.VarSel) issue(new BadArgCountError(expr.pos, ((Tree.VarSel)id).name, expectedArgCount, gotArgCount));
            else issue(new MyFunCallError2(expr.pos, expectedArgCount, gotArgCount));
        }
        int end = expectedArgCount <= gotArgCount ? expectedArgCount : gotArgCount;
        for (int i=0; i<end; i++) {
            if (expr.args.get(i).type.noError() && !expr.args.get(i).type.subtypeOf(expectedArgTypes.get(i))) {
                    issue(new BadArgTypeError(expr.args.get(i).pos, i+1, expr.args.get(i).type.toString(), expectedArgTypes.get(i).toString()));
                }
        }
    }

    @Override
    public void visitClassTest(Tree.ClassTest expr, ScopeStack ctx) {
        //System.out.println("Typer visitClassTest");
        expr.obj.accept(this, ctx);
        expr.type = BuiltInType.BOOL;

        if (!expr.obj.type.isClassType()) {
            issue(new NotClassError(expr.obj.type.toString(), expr.pos));
        }
        var clazz = ctx.lookupClass(expr.is.name);
        if (clazz.isEmpty()) {
            issue(new ClassNotFoundError(expr.pos, expr.is.name));
        } else {
            expr.symbol = clazz.get();
        }
    }

    @Override
    public void visitClassCast(Tree.ClassCast expr, ScopeStack ctx) {
        //System.out.println("Typer visitClassCast");
        expr.obj.accept(this, ctx);

        if (!expr.obj.type.isClassType()) {
            issue(new NotClassError(expr.obj.type.toString(), expr.pos));
        }

        var clazz = ctx.lookupClass(expr.to.name);
        if (clazz.isEmpty()) {
            issue(new ClassNotFoundError(expr.pos, expr.to.name));
            expr.type = BuiltInType.ERROR;
        } else {
            expr.symbol = clazz.get();
            expr.type = expr.symbol.type;
        }
    }

    @Override
    public void visitLocalVarDef(Tree.LocalVarDef stmt, ScopeStack ctx) {
        //System.out.println("Typer visitLocalVarDef " + stmt.name);
        if (stmt.symbol.type.eq(BuiltInType.WAIT)){
            assert(!stmt.initVal.isEmpty());//var类型等号后面不能为空
            var initVal = stmt.initVal.get();
            localVarDefPos = Optional.ofNullable(stmt.id.pos);
            initVal.accept(this, ctx);
            localVarDefPos = Optional.empty();
            if (initVal.type == BuiltInType.VOID) {
                stmt.symbol.type = BuiltInType.ERROR;
                issue(new BadVarTypeError(stmt.pos, stmt.name));
            } else {
                stmt.symbol.type = initVal.type;
            }
        } else {
            if (stmt.initVal.isEmpty()) return;

            var initVal = stmt.initVal.get();
            localVarDefPos = Optional.ofNullable(stmt.id.pos);
            initVal.accept(this, ctx);
            localVarDefPos = Optional.empty();
            var lt = stmt.symbol.type;
            var rt = initVal.type;

            if (lt.noError() && !rt.subtypeOf(lt)) {
                //System.out.println("IncompatBinOpError");
                issue(new IncompatBinOpError(stmt.assignPos, lt.toString(), "=", rt.toString()));
            }
        }
    }

    @Override
    public void visitLambda(Tree.Lambda lambda, ScopeStack ctx) {
        //System.out.println("Typer visitLambda lambda@"+lambda.pos);
        if (lambda.expr != null) {
            ctx.open(lambda.symbol.formalScope);
            ctx.open(lambda.symbol.formalScope.nestedLocalScope());
            lambda.expr.accept(this, ctx);
            ctx.close();
            ctx.close();
            lambda.symbol.type.returnType = lambda.expr.type;
        } else if (lambda.body != null) {
            ctx.open(lambda.symbol.formalScope);
            lambda.body.accept(this, ctx);
            lambda.symbol.type.returnType = getLambdaBlockReturnType(lambda, ctx);//不能在close lambdaformalscope后做
            //System.out.println("Typer visitLambda - lambdaReturnType is" + lambda.symbol.type.returnType);
            ctx.close();
        } else {
            //error
        }
    }

    private Type getLambdaBlockReturnType(Tree.Lambda lambda, ScopeStack ctx) {
        //System.out.println("Typer getLambdaBlockReturnType");
        if (ctx.currentLambdaReturnTypeList().isEmpty()) { //内部没有返回值
            //System.out.println("Typer getLambdaBlockReturnType - 内部没有返回值");
            return BuiltInType.VOID;
        } else { //内部有返回值
            //System.out.println("Typer getLambdaBlockReturnType - 内部有返回值");

            if (!lambda.body.isClosed) {
                //System.out.println("Typer getLambdaBlockReturnType - !lambda.body.isClosed");

                for (var type : ctx.currentLambdaReturnTypeList()) {
                    if (!type.eq(BuiltInType.VOID)) {
                        issue(new MissingReturnError(lambda.body.pos));
                        break;
                    }
                }
            }

            Type type = getUpperBound(ctx.currentLambdaReturnTypeList());
            //System.out.println("Typer getLambdaBlockReturnType - getUpperBound is " + type);

            if (type.eq(BuiltInType.ERROR)) {
                //System.out.println("Typer getLambdaBlockReturnType - MyLambdaError2");
                issue(new MyLambdaError2(lambda.body.pos));
            }
            return type;
        }
    }

    private Type getUpperBound(List<Type> typeList) {
        //System.out.println("Typer getUpperBound of " + typeList);

        Type selected = BuiltInType.NULL;
        for (Type type : typeList) if (!type.eq(BuiltInType.NULL)) { selected = type; break; } //选择一个非null类型
        if (selected.eq(BuiltInType.NULL)) return BuiltInType.NULL; //如果全是 BuiltInType.NULL 则返回 BuiltInType.NULL

        if (selected.isBaseType() || selected.isVoidType() || selected.isArrayType()) {
            //System.out.println("Typer getUpperBound - 基本类型");
            for (Type type : typeList) if (!type.eq(selected)) return BuiltInType.ERROR;
            return selected;
        } else if (selected.isClassType()) { //处理classtype
            //System.out.println("Typer getUpperBound - class类型");
            for (Type type : typeList) {
                if (type.eq(BuiltInType.NULL)) continue; //遇到 null 跳过
                if (!type.isClassType()) return BuiltInType.ERROR; // 遇到不是 classtype 报错
            }
            while (true) {
                boolean satisfied = true;
                for (Type type : typeList){
                    if (type.eq(BuiltInType.NULL)) continue;
                    if (!type.subtypeOf(selected)) { satisfied = false; break; }
                }
                if (satisfied){ //所有的type都是selected的子类
                    return selected;
                } else {
                    if (((ClassType)selected).superType.isPresent()) selected = ((ClassType)selected).superType.get();
                    else return BuiltInType.ERROR;
                }
            }
        } else if (selected.isFuncType()) { //处理 FunType
            //System.out.println("Typer getUpperBound - 函数类型");
            List<Type> r = new ArrayList<>();
            List<List<Type>> t = new ArrayList<>();
            int argCount = ((FunType)selected).arity();//参数个数
            //System.out.println("Typer getUpperBound - 函数类型 argCount is " + argCount);
            for (int i=0; i<argCount; i++) t.add(new ArrayList<Type>());
            for (Type type : typeList) {
                if (type.eq(BuiltInType.NULL)) continue;
                if (!type.isFuncType()) return BuiltInType.ERROR;
                if (argCount != ((FunType)type).arity()) return BuiltInType.ERROR;

                r.add(((FunType)type).returnType);
                for (int i=0; i<argCount; i++) t.get(i).add(((FunType)type).argTypes.get(i));
            }

            Type R = getUpperBound(r);
            if (R.eq(BuiltInType.ERROR)) return BuiltInType.ERROR;

            List<Type> T = new ArrayList<>();

            for (int i=0; i<argCount; i++) {
                Type lowerBound = getLowerBound(t.get(i));
                //System.out.println("Typer getUpperBound - 函数类型 从 lowerBound 中返回 " + lowerBound);
                if (lowerBound.eq(BuiltInType.ERROR)) return BuiltInType.ERROR;
                T.add(lowerBound);
            }
            return new FunType(R, T);
        }
        return BuiltInType.ERROR;
    }

    private Type getLowerBound(List<Type> typeList) {
        //System.out.println("Typer getLowerBound of " + typeList);

        Type selected = BuiltInType.NULL;
        for (Type type : typeList) if (!type.eq(BuiltInType.NULL)) { selected = type; break; } //选择一个非null类型
        if (selected.eq(BuiltInType.NULL)) return BuiltInType.NULL; //如果全是 BuiltInType.NULL 则返回 BuiltInType.NULL

        if (selected.isBaseType() || selected.isVoidType() || selected.isArrayType()) {
            //System.out.println("Typer getLowerBound - 基本类型");
            for (Type type : typeList) if (!type.eq(selected)) return BuiltInType.ERROR;
            return selected;
        } else if (selected.isClassType()) { //处理classtype
            //System.out.println("Typer getLowerBound - class类型");
            for (Type type : typeList) {
                if (type.eq(BuiltInType.NULL)) continue; //遇到 null 跳过
                if (!type.isClassType()) {
                    //System.out.println("Typer getLowerBound - class类型 - !type.isClassType " + type);
                    return BuiltInType.ERROR;
                }// 遇到不是 classtype 报错
                if (selected.subtypeOf(type)) continue;
                if (type.subtypeOf(selected)) { selected = type; continue; }
                if (!type.subtypeOf(selected) && !selected.subtypeOf(type)) {
                    //System.out.println("Typer getLowerBound - class类型 - 互不是父子 " + type);
                    return BuiltInType.ERROR;
                }
            }
            return selected;
        } else if (selected.isFuncType()) { //处理 FunType
            //System.out.println("Typer getLowerBound - 函数类型");
            List<Type> r = new ArrayList<>();
            List<List<Type>> t = new ArrayList<>();
            int argCount = ((FunType)selected).arity();//参数个数
            for (int i=0; i<argCount; i++) t.add(new ArrayList<Type>());
            for (Type type : typeList) {
                if (type.eq(BuiltInType.NULL)) continue;
                if (!type.isFuncType()) return BuiltInType.ERROR;
                if (argCount != ((FunType)type).arity()) return BuiltInType.ERROR;

                r.add(((FunType)type).returnType);
                for (int i=0; i<argCount; i++) t.get(i).add(((FunType)type).argTypes.get(i));
            }

            Type R = getLowerBound(r);
            if (R.eq(BuiltInType.ERROR)) return BuiltInType.ERROR;

            List<Type> T = new ArrayList<>();
            for (List<Type> arg : t) {
                Type lowerBound = getUpperBound(arg);
                if (lowerBound.eq(BuiltInType.ERROR)) return BuiltInType.ERROR;
                T.add(lowerBound);
            }
            return new FunType(R, T);
        }
        return BuiltInType.ERROR;
    }

    // Only usage: check if an initializer cyclically refers to the declared variable, e.g. var x = x + 1
    private Optional<Pos> localVarDefPos = Optional.empty();
}
