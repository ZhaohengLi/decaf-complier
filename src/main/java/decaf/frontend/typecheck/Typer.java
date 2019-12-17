package decaf.frontend.typecheck;

import decaf.driver.Config;
import decaf.driver.Phase;
import decaf.driver.error.*;
import decaf.frontend.scope.LambdaFormalScope;
import decaf.frontend.scope.ScopeStack;
import decaf.frontend.symbol.ClassSymbol;
import decaf.frontend.symbol.MethodSymbol;
import decaf.frontend.symbol.Symbol;
import decaf.frontend.symbol.VarSymbol;
import decaf.frontend.symbol.LambdaSymbol;
import decaf.frontend.tree.Pos;
import decaf.frontend.tree.Tree;
import decaf.frontend.tree.Tree.IndexSel;
import decaf.frontend.tree.Tree.Lambda;
import decaf.frontend.tree.Tree.UnaryOp;
import decaf.frontend.type.ArrayType;
import decaf.frontend.type.BuiltInType;
import decaf.frontend.type.ClassType;
import decaf.frontend.type.Type;
import decaf.frontend.type.FunType;
import decaf.lowlevel.log.IndentPrinter;
import decaf.printing.PrettyScope;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Stack;

/**
 * The typer phase: type check abstract syntax tree and annotate nodes with inferred (and checked) types.
 */
public class Typer extends Phase<Tree.TopLevel, Tree.TopLevel> implements TypeLitVisited {

    private List<List<Type>> typeListStack = new ArrayList<List<Type>>();

    private List<String> defines = new ArrayList<String>();


    /**
     * To determine if a break statement is legal or not, we need to know if we are inside a loop, i.e.
     * loopLevel {@literal >} 1?
     * <p>
     * Increase this counter when entering a loop, and decrease it when leaving a loop.
     */
        @Override
        public void visitFor(Tree.For loop, ScopeStack ctx) {
            ctx.open(loop.scope);
            loop.init.accept(this, ctx);
            checkTestExpr(loop.cond, ctx);
            loop.update.accept(this, ctx);
            loopLevel++;
            for (var stmt : loop.body.stmts) {
                stmt.accept(this, ctx);
                if(stmt.isClose) {
                    loop.isClose = true;
                }
            }
            loopLevel--;
            ctx.close();
        }

        @Override
        public void visitBreak(Tree.Break stmt, ScopeStack ctx) {
            if (loopLevel == 0) {
                issue(new BreakOutOfLoopError(stmt.pos));
            }
        }

        @Override
        public void visitPrint(Tree.Print stmt, ScopeStack ctx) {
            int i = 0;
            for (var expr : stmt.exprs) {
                expr.accept(this, ctx);
                i++;
                if (expr.type.noError() && !expr.type.isBaseType()) {
                    issue(new BadPrintArgError(expr.pos, Integer.toString(i), expr.type.toString()));
                }
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
        if(block.isLambda) {
            ctx.open(block.lambdaLocalScope);
        }
        else
            ctx.open(block.scope);
        for (var stmt : block.stmts) {
            stmt.accept(this, ctx);
            if(stmt.isClose) {
                block.isClose = true;
            }
        }
        ctx.close();
        block.returns = !block.stmts.isEmpty() && block.stmts.get(block.stmts.size() - 1).returns;
    }


    /**
     * To determine if a break statement is legal or not, we need to know if we are inside a loop, i.e.
     * loopLevel {@literal >} 1?
     * <p>
     * Increase this counter when entering a loop, and decrease it when leaving a loop.
     */
    @Override
    public void visitAssign(Tree.Assign stmt, ScopeStack ctx) {
        stmt.lhs.accept(this, ctx);
        stmt.rhs.accept(this, ctx);
        var lt = stmt.lhs.type;
        var rt = stmt.rhs.type;

        if(stmt.lhs instanceof Tree.VarSel && ((Tree.VarSel)stmt.lhs).isMemberFuncName) {
            issue(new BadValueAssignError(((Tree.VarSel) stmt.lhs).name, stmt.pos));
            return;
        }

        if(ctx.currentLambdaScope().isLambdaFormalScope() && (stmt.lhs instanceof Tree.VarSel)) {
            var lambdaFormalScope = (LambdaFormalScope)ctx.currentLambdaScope();
            Symbol symbol =  ((Tree.VarSel)stmt.lhs).symbol;
            if(!((Tree.VarSel)stmt.lhs).type.eq(BuiltInType.ERROR)) {
                if (symbol.lambdaDomain() == null) {
                    if (!symbol.domain().isClassScope()){
                        issue(new BadCapValueAssignError(stmt.pos));
                    }
                } else {
                    var now = symbol.lambdaDomain();
                    if (now != lambdaFormalScope) {
                        issue(new BadCapValueAssignError(stmt.pos));
                        return;
                    }
                }
            }
        }
        if (!rt.subtypeOf(lt) && lt.noError()) {
            issue(new IncompatBinOpError(stmt.pos, lt.toString(), "=", rt.toString()));
        }
    }
    /**
     * To determine if a break statement is legal or not, we need to know if we are inside a loop, i.e.
     * loopLevel {@literal >} 1?
     * <p>
     * Increase this counter when entering a loop, and decrease it when leaving a loop.
     */
        @Override
        public Tree.TopLevel transform(Tree.TopLevel tree) {
            var ctx = new ScopeStack(tree.globalScope);
            tree.accept(this, ctx);
            return tree;
        }

        @Override
        public void visitClassDef(Tree.ClassDef clazz, ScopeStack ctx) {
            ctx.open(clazz.symbol.scope);
            for (var field : clazz.fields) {
                field.accept(this, ctx);
            }
            ctx.close();
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
            for (var clazz : program.classes) {
                clazz.accept(this, ctx);
            }
        }

    @Override
    public void visitExprEval(Tree.ExprEval stmt, ScopeStack ctx) {
        stmt.expr.accept(this, ctx);
    }
    /**
     * To determine if a break statement is legal or not, we need to know if we are inside a loop, i.e.
     * loopLevel {@literal >} 1?
     * <p>
     * Increase this counter when entering a loop, and decrease it when leaving a loop.
     */

    @Override
    public void visitIf(Tree.If stmt, ScopeStack ctx) {
        checkTestExpr(stmt.cond, ctx);
        stmt.trueBranch.accept(this, ctx);
        stmt.falseBranch.ifPresent(b -> b.accept(this, ctx));
        if(stmt.trueBranch.isClose && stmt.falseBranch.isPresent() && stmt.falseBranch.get().isClose) {
            stmt.isClose = true;
        }
        // if-stmt returns a value iff both branches return
        stmt.returns = stmt.trueBranch.returns && stmt.falseBranch.isPresent() && stmt.falseBranch.get().returns;
    }

    @Override
    public void visitWhile(Tree.While loop, ScopeStack ctx) {
        checkTestExpr(loop.cond, ctx);
        loopLevel++;
        loop.body.accept(this, ctx);
        loopLevel--;
        if(loop.body.isClose)
            loop.isClose = true;
    }
    /**
     * To determine if a break statement is legal or not, we need to know if we are inside a loop, i.e.
     * loopLevel {@literal >} 1?
     * <p>
     * Increase this counter when entering a loop, and decrease it when leaving a loop.
     */



    @Override
    public void visitReturn(Tree.Return stmt, ScopeStack ctx) {
        stmt.expr.ifPresent(e -> e.accept(this, ctx));
        var actual = stmt.expr.map(e -> e.type).orElse(BuiltInType.VOID);
        var currentLambdaScope = ctx.currentLambdaScope();
        if(currentLambdaScope.isFormalScope()) {
            var expected = ctx.currentMethod().type.returnType;
            if (actual.noError() && !actual.subtypeOf(expected)) {
                issue(new BadReturnTypeError(stmt.pos, expected.toString(), actual.toString()));
            }
        } else if(currentLambdaScope.isLambdaFormalScope()) {
            stmt.isClose = true;
            typeListStack.get(typeListStack.size() - 1).add(actual);
        }
        stmt.returns = stmt.expr.isPresent();
    }
    /**
     * To determine if a break statement is legal or not, we need to know if we are inside a loop, i.e.
     * loopLevel {@literal >} 1?
     * <p>
     * Increase this counter when entering a loop, and decrease it when leaving a loop.
     */


    private void checkTestExpr(Tree.Expr expr, ScopeStack ctx) {
        expr.accept(this, ctx);
        if (expr.type.noError() && !expr.type.eq(BuiltInType.BOOL)) {
            issue(new BadTestExpr(expr.pos));
        }
    }

    public Type getLoBound(List<Type> tylist)
    {
        Type t = BuiltInType.NULL;
        for(var type : tylist) {
            if(type != BuiltInType.NULL) {
                t = type;
                break;
            }
        }
        if(t == BuiltInType.NULL)
            return t;
        if(t.isArrayType() || t.isBaseType() || t.isVoidType()){
            for( var type : tylist){
                if(!type.eq(t))
                    return BuiltInType.ERROR;
            }
            return t;
        }
        if(t.isClassType()){
            Type lowT = t;
            int total = tylist.size();
            for(int i = 0;i < total;i++){
                for(int j = i+1;j < total;j++){
                    if(tylist.get(i).subtypeOf(tylist.get(j))){
                        if(tylist.get(i).subtypeOf(lowT))
                            lowT = tylist.get(i);
                    } else if(tylist.get(j).subtypeOf(tylist.get(i))){
                            if(tylist.get(j).subtypeOf(lowT))
                                lowT = tylist.get(j);
                    } else {
                        return BuiltInType.ERROR;
                    }
                }
            }

            return lowT;
        }

        if(t.isFuncType()) {
            var m = ((FunType)t).arity();
            List<Type> TTypes = new ArrayList<>();
            List<List<Type>> Tf = new ArrayList<>();
            List<Type> R = new ArrayList<>();

            for(int i = 0;i < m;i++)
                Tf.add(new ArrayList<Type>());

            for(var type : tylist) {
                if(!type.isFuncType() || m != ((FunType)type).arity())
                    return BuiltInType.ERROR;
                for(int i = 0;i < m;i++)
                    Tf.get(i).add(((FunType)type).argTypes.get(i));
                R.add(((FunType)type).returnType);
            }System.out.println("form transform");

            for(int i = 0;i < m;i++) {
                Type Ti = getUpBound(Tf.get(i));
                if(Ti.eq(BuiltInType.ERROR))
                    return BuiltInType.ERROR;
                TTypes.add(Ti);
            }

            Type RTypes = getLoBound(R);
            if(RTypes.eq(BuiltInType.ERROR))
                return BuiltInType.ERROR;
            return new FunType(RTypes, TTypes);
        }
        return BuiltInType.ERROR;
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
        if(op == UnaryOp.NEG)
            return operand.eq(BuiltInType.INT);
        if(op == UnaryOp.NOT)
            return operand.eq(BuiltInType.BOOL);
        // return switch (op) {
        //     case NEG -> operand.eq(BuiltInType.INT); // if e : int, then -e : int
        //     case NOT -> operand.eq(BuiltInType.BOOL); // if e : bool, then !e : bool
        // };
        return allowClassNameVar;
    }

    public Type resultTypeOf(Tree.UnaryOp op) {
        if(op == UnaryOp.NEG)
            return BuiltInType.INT;
        if(op == UnaryOp.NOT)
            return BuiltInType.BOOL;
        // return switch (op) {
        //     case NEG -> BuiltInType.INT;
        //     case NOT -> BuiltInType.BOOL;
        // };
        return null;
    }

    @Override
    public void visitBinary(Tree.Binary expr, ScopeStack ctx) {
        expr.lhs.accept(this, ctx);
        expr.rhs.accept(this, ctx);
        var t1 = expr.lhs.type;
        var t2 = expr.rhs.type;
        if (t1.noError() && t2.noError() && !compatible(expr.op, t1, t2)) {
            issue(new IncompatBinOpError(expr.pos, t1.toString(), Tree.opStr(expr.op), t2.toString()));
        }
        expr.type = resultTypeOf(expr.op);
    }

    public void getIter(Tree.VarSel expr) {
        if (expr.symbol != null) {
            if (!lambdaStack.isEmpty() && expr.symbol.isVarSymbol()) {
                var variable = (VarSymbol) expr.symbol;
                if (expr.receiver.isEmpty() || expr.receiver.get() instanceof Tree.This){
                    ListIterator<Tree.Lambda> iter = lambdaStack.listIterator(lambdaStack.size());
                    if (variable.name.equals("this") || (expr.receiver.isPresent() && expr.receiver.get() instanceof Tree.This) ) {
                        while (iter.hasPrevious()) {
                            var curr = iter.previous();
                            curr.required = true;
                        }
                    } else {
                        while (iter.hasPrevious()) {
                            var curr = iter.previous();
                            if (variable.lambdaDomain() == null || variable.lambdaDomain() != curr.formalScope)
                                curr.currSymbol.put(variable, null);
                            else
                                break;
                        }

                    }
                }
            }
        }
    }

    @Override
    public void visitIndexSel(Tree.IndexSel expr, ScopeStack ctx) {
        expr.array.accept(this, ctx);
        expr.index.accept(this, ctx);
        var at = expr.array.type;
        var it = expr.index.type;
        if(at != null) {
            if (!at.isArrayType()) {
                if (at != BuiltInType.ERROR)
                    issue(new NotArrayError(expr.array.pos));
                expr.type = BuiltInType.ERROR;
                return;
            }
        }

        expr.type = ((ArrayType) at).elementType;
        if (!it.eq(BuiltInType.INT)) {
            issue(new SubNotIntError(expr.pos));
        }
    }

    @Override
    public void visitCall(Tree.Call expr, ScopeStack ctx) {
        expr.receiver.get().accept(this, ctx);
        if(!expr.receiver.get().type.noError()) {
            expr.type = BuiltInType.ERROR;
            return;
        }
        if(!expr.receiver.get().type.isFuncType()) {
            issue(new NotCallableError(expr.pos, expr.receiver.get().type.toString()));
            expr.type = BuiltInType.ERROR;
            return;
        }

        if (expr.receiver.get() instanceof Tree.VarSel && ((Tree.VarSel)expr.receiver.get()).isArrayLen) {
            if(!expr.args.isEmpty()) {
                issue(new BadLengthArgError(expr.pos, expr.args.size()));
            }
            expr.isArrayLength = true;
            expr.type = BuiltInType.INT;
            return;
        }
        typeCall(expr, ctx);
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
    public void visitThis(Tree.This expr, ScopeStack ctx) {
        if (ctx.currentMethod().isStatic()) {
            issue(new ThisInStaticFuncError(expr.pos));
        }
        expr.type = ctx.currentClass().type;
    }

    public boolean compatible(Tree.BinaryOp op, Type lhs, Type rhs) {System.out.println("form transform");
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

    private boolean allowClassNameVar = false;

    public Type resultTypeOf(Tree.BinaryOp op) {
        if (op.compareTo(Tree.BinaryOp.ADD) >= 0 && op.compareTo(Tree.BinaryOp.MOD) <= 0) { // arith
            return BuiltInType.INT;
        }
        return BuiltInType.BOOL;
    }


    @Override
    public void visitLambda(Tree.Lambda lambda, ScopeStack ctx) {System.out.println("form transform");
        lambda.required = false;
        lambdaStack.push(lambda);
        ctx.open(lambda.formalScope);
        if(lambda.expr instanceof Tree.Expr) {
            ctx.open(lambda.localScope);
            ((Tree.Expr)lambda.expr).accept(this, ctx);
            ctx.close();
            ctx.close();
            lambda.type = new FunType(((Tree.Expr)lambda.expr).type, lambda.argTypes);
        } else {
            typeListStack.add(new ArrayList<Type>());
            ((Tree.Block)lambda.expr).accept(this, ctx);
            Type retyblock = getLambdaReTyBlock((Tree.Block)lambda.expr);
            typeListStack.remove(typeListStack.size()-1);
            ctx.close();
            lambda.type = new FunType(retyblock, lambda.argTypes);
        }
        var symbol = new LambdaSymbol("lambda@" + lambda.pos, (FunType)lambda.type, lambda.pos, lambda.formalScope);
        lambda.lambdaSymbol = symbol;
        ctx.declare(symbol);
        lambdaStack.pop();
    }

    public Typer(Config config) {
        super("typer", config);
    }



    @Override
    public void visitMethodDef(Tree.MethodDef method, ScopeStack ctx) {
        ctx.open(method.symbol.scope);
        if(!method.body.isEmpty()) {System.out.println("form transform");
            method.body.get().accept(this, ctx);
            if (!method.symbol.type.returnType.isVoidType() && !method.body.get().returns) {
                issue(new MissingReturnError(method.body.get().pos));
            }
        }
        ctx.close();
    }

    @Override
    public void visitNewArray(Tree.NewArray expr, ScopeStack ctx) {
        expr.elemType.accept(this, ctx);
        expr.length.accept(this, ctx);
        var et = expr.elemType.type;System.out.println("form transform");
        var lt = expr.length.type;

        if (et.isVoidType()) {
            issue(new BadArrElementError(expr.elemType.pos));
            expr.type = BuiltInType.ERROR;
        } else {
            expr.type = new ArrayType(et);
        }
        if (lt != null && lt.noError() && !lt.eq(BuiltInType.INT)) {
            issue(new BadNewArrayLength(expr.length.pos));
        }
    }

    @Override
    public void visitNewClass(Tree.NewClass expr, ScopeStack ctx) {
        var clazz = ctx.lookupClass(expr.clazz.name);
        if (clazz.isPresent()) {
            if(clazz.get().isAbstract()) {
                issue(new InsAbsClassError(expr.pos, clazz.get().name));
                expr.type = BuiltInType.ERROR;
            } else {
                expr.symbol = clazz.get();
                expr.type = expr.symbol.type;System.out.println("form transform");
            }
        } else {
            issue(new ClassNotFoundError(expr.pos, expr.clazz.name));
            expr.type = BuiltInType.ERROR;
        }
    }

    public Type getLambdaReTyBlock(Tree.Block block) {
        if(typeListStack.get(typeListStack.size() - 1).isEmpty()){
            return BuiltInType.VOID;
        } else {
            if(!block.isClose) {
                for(var t:typeListStack.get(typeListStack.size() - 1)) {
                    if (!t.eq(BuiltInType.VOID)) {
                        issue(new MissingReturnError(block.pos));
                        break;
                    }
                }
            }
            Type ty = getUpBound(typeListStack.get(typeListStack.size() - 1));
            if(ty.eq(BuiltInType.ERROR)) {
                issue(new InReTyError(block.pos));
        }System.out.println("form transform");
        return ty;
        }
    }



    @Override
    public void visitVarSel(Tree.VarSel expr, ScopeStack ctx) {
        if (expr.receiver.isEmpty()) {
            // Variable, which should be complicated since a legal variable could refer to a local var,
            // a visible member var, and a class name.
            var symbol = ctx.lookupBefore(expr.name, localVarDefPos.orElse(expr.pos));
            if (symbol.isPresent() && !defines.contains(symbol.get().name)) {
                if (symbol.get().isVarSymbol()) {
                    var va = (VarSymbol) symbol.get();
                    expr.symbol = va;
                    expr.type = va.type;
                    if (va.isMemberVar()) {
                        if (ctx.currentMethod().isStatic()) {
                            issue(new RefNonStaticError(expr.pos, ctx.currentMethod().name, expr.name));
                        } else {
                            expr.setThis();System.out.println("form transform");
                        }
                    }
                    getIter(expr);
                    return;
                }

                if(symbol.get().isMethodSymbol()) {
                    var fun = (MethodSymbol)symbol.get();
                    expr.type = fun.type;
                    expr.symbol = fun;
                    if(fun.isMemberFunc()) {
                        expr.isMemberFuncName = true;
                        if(ctx.currentMethod().isStatic() && !fun.isStatic())
                            issue(new NotStaticError(expr.pos, ctx.currentMethod().name, expr.name));
                        else {
                            expr.setThis();
                        }
                    }
                    return;
                }
                if (symbol.get().isClassSymbol() && allowClassNameVar) { // special case: a class name
                    var clazz = (ClassSymbol) symbol.get();
                    expr.type = clazz.type;
                    expr.isClassName = true;System.out.println("form transform");
                    return;
                }
            }

            expr.type = BuiltInType.ERROR;
            issue(new UndeclVarError(expr.pos, expr.name));
            return;
        }

        var receiver = expr.receiver.get();
        allowClassNameVar = true;
        receiver.accept(this, ctx);
        allowClassNameVar = false;
        var rt = receiver.type;System.out.println("form transform");
        expr.type = BuiltInType.ERROR;

        if (!rt.noError())
            return;

        if (rt.isArrayType() && expr.name.equals("length")) {
            expr.type = new FunType(BuiltInType.INT, new ArrayList<Type>());
            expr.isArrayLen = true;
            getIter(expr);System.out.println("form transform");
            return;
        }


        if (!rt.isClassType()) {
            issue(new NotClassFieldError(expr.pos, expr.name, rt.toString()));
            return;
        }

        var ct = (ClassType) rt;
        var field = ctx.getClass(ct.name).scope.lookup(expr.name);

        if (receiver instanceof Tree.VarSel) {
            var v = (Tree.VarSel) receiver;
            if (v.isClassName) {System.out.println("form transform");
                // special case like MyClass.foo: report error cannot access field 'foo' from 'class : MyClass'
                if(field.isPresent() && (field.get().isVarSymbol() || (!((MethodSymbol)field.get()).isStatic() && field.get().isMethodSymbol()))) {
                    issue(new NotClassFieldError(expr.pos, expr.name, ctx.getClass(v.name).type.toString()));
                    return;
                }

            }
        }
        if (field.isPresent()) {
            if(field.get().isVarSymbol()){
                String fuck = "Fuuuuuuuuck U";
                var va = (VarSymbol) field.get();
                if (va.isMemberVar()) {
                    expr.symbol = va;
                    expr.type = va.type;
                    if (!ctx.currentClass().type.subtypeOf(va.getOwner().type)) {
                        issue(new FieldNotAccessError(expr.pos, expr.name, ct.toString()));
                    }
                }
            } else if (field.get().isMethodSymbol()) {
                var func = (MethodSymbol) field.get();
                if (func.isMemberFunc()) {System.out.println("form transform");
                    expr.isMemberFuncName = true;
                    expr.symbol = func;
                    expr.type = func.type;
                }
            }
        } else if (field.isEmpty()) {
            issue(new FieldNotFoundError(expr.pos, expr.name, ct.toString()));
        } else {
            issue(new NotClassFieldError(expr.pos, expr.name, ct.toString()));
        }System.out.println("form transform");
        getIter(expr);
    }


    private void typeCall(Tree.Call call,  ScopeStack ctx) {
        FunType func = ((FunType)call.receiver.get().type);
        if (call.receiver.get() instanceof Tree.Lambda)
            call.symbol = ((Tree.Lambda)call.receiver.get()).lambdaSymbol;
        else if (call.receiver.get() instanceof Tree.VarSel)
            call.symbol = ((Tree.VarSel)call.receiver.get()).symbol;
        else if (call.receiver.get() instanceof Tree.Call)
            call.symbol = ((Tree.Call)call.receiver.get()).symbol;
        call.type = func.returnType;
        var args = call.args;System.out.println("form transform");
        for (var arg : args)
            arg.accept(this, ctx);

        if (func.arity() != call.args.size()) {
            if (call.receiver.get() instanceof Tree.VarSel) {
                issue(new BadArgError(call.pos, ((Tree.VarSel) call.receiver.get()).variable.name, func.arity(), call.args.size()));
            } else {
                issue(new BadArgError(call.pos, func.arity(), call.args.size()));
            }
        }
        var iter1 = func.argTypes.iterator();
        var iter2 = call.args.iterator();

        for (int i = 1; iter1.hasNext() && iter2.hasNext(); i++) {
            Type t1 = iter1.next();
            Tree.Expr e = iter2.next();
            Type t2 = e.type;
            if (t2.noError() && !t2.subtypeOf(t1)) {
                issue(new BadArgTypeError(e.pos, i, t2.toString(), t1.toString()));
            }
        }
    }

    @Override
    public void visitClassTest(Tree.ClassTest expr, ScopeStack ctx) {
        expr.obj.accept(this, ctx);
        expr.type = BuiltInType.BOOL;
System.out.println("form transform");
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
        expr.obj.accept(this, ctx);
System.out.println("form transform");
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

    public Stack<Tree.Lambda> lambdaStack = new Stack<>();

    @Override
    public void visitLocalVarDef(Tree.LocalVarDef stmt, ScopeStack ctx) {
        if (stmt.initVal.isEmpty()) return;
System.out.println("form transform");
        var initVal = stmt.initVal.get();
        localVarDefPos = Optional.ofNullable(stmt.id.pos);
        defines.add(stmt.id.name);
        initVal.accept(this, ctx);
        defines.remove(defines.size() - 1);
        localVarDefPos = Optional.empty();
        var lt = stmt.symbol.type;
        var rt = initVal.type;

        if(lt == null) {
            if(rt.isVoidType()) {
                stmt.symbol.type = BuiltInType.ERROR;
                issue(new BadVarTypeError(stmt.id.pos, stmt.id.name));

            } else
                stmt.symbol.type = rt;
            return;
        }
System.out.println("form transform");
        if (lt.noError() && !rt.subtypeOf(lt)) {
            issue(new IncompatBinOpError(stmt.assignPos, lt.toString(), "=", rt.toString()));
        }
    }


    public Type getUpBound(List<Type> tylist) {
        Type t = BuiltInType.NULL;
        for(var type : tylist) {
            if(type != BuiltInType.NULL) {
                t = type;
                break;
            }
        }

        if(t == BuiltInType.NULL)
            return t;
System.out.println("form transform");
        if(t.isArrayType() || t.isVoidType() || t.isBaseType()) {
            for(var type : tylist) {
                if(!type.eq(t)) {
                    return BuiltInType.ERROR;
                }
            }
            return t;
        }

        if(t.isClassType()) {
            var p = t;
            while(true) {
                var succ = true;
                for(var type : tylist) {
                    if(!type.subtypeOf(p)) {
                        succ = false;
                    }
                }

                if(succ)
                    return p;
                else if(((ClassType)p).superType.isPresent())
                    p = ((ClassType)p).superType.get();
                else
                    break;System.out.println("form transform");
            }
            return BuiltInType.ERROR;
        }

        if(t.isFuncType()) {
            var m = ((FunType)t).arity();
            List<Type> TTypes = new ArrayList<>();
            List<List<Type>> Tf = new ArrayList<>();
            List<Type> R = new ArrayList<>();

            for(int i = 0;i < m;i++) {
                Tf.add(new ArrayList<Type>());System.out.println("form transform");
            }

            for(var type : tylist) {
                if(m != ((FunType)type).arity() || !type.isFuncType())
                    return BuiltInType.ERROR;

                for(int i = 0;i < m;i++){
                    Tf.get(i).add(((FunType)type).argTypes.get(i));
                }
                R.add(((FunType)type).returnType);
            }

            for(int i = 0;i < m;i++){
                Type Ti = getLoBound(Tf.get(i));
                if(Ti.eq(BuiltInType.ERROR))
                    return BuiltInType.ERROR;
                TTypes.add(Ti);
            }
            Type RTypes = getUpBound(R);

            if(RTypes.eq(BuiltInType.ERROR))
                return BuiltInType.ERROR;
            return new FunType(RTypes, TTypes);
        }
        return BuiltInType.ERROR;
    }

    // Only usage: check if an initializer cyclically refers to the declared variable, e.g. var x = x + 1
    private Optional<Pos> localVarDefPos = Optional.empty();
}
