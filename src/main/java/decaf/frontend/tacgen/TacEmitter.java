package decaf.frontend.tacgen;

import decaf.frontend.symbol.VarSymbol;
import decaf.frontend.tree.Tree;
import decaf.frontend.tree.Visitor;
import decaf.frontend.type.BuiltInType;
import decaf.lowlevel.instr.Temp;
import decaf.lowlevel.label.Label;
import decaf.lowlevel.tac.FuncVisitor;
import decaf.lowlevel.tac.Intrinsic;
import decaf.lowlevel.tac.RuntimeError;
import decaf.lowlevel.tac.TacInstr;

import java.util.ArrayList;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Function;

import decaf.frontend.symbol.MethodSymbol;
import java.lang.reflect.Method;
import decaf.frontend.symbol.LambdaSymbol;
import decaf.frontend.type.FunType;
/**
 * TAC emitter. Traverse the tree and emit TAC.
 * <p>
 * When emitting TAC, we use utility methods from {@link FuncVisitor}, so that we don't bother
 * ourselves understanding the underlying format of TAC instructions.
 * <p>
 * See {@link #emitIfThen} for the usage of {@link Consumer}.
 */
public interface TacEmitter extends Visitor<FuncVisitor> {


    Stack<Tree.Lambda> lambdaStack = new Stack<>();
    Stack<Label> loopExits = new Stack<>();

    @Override
    default void visitLambda(Tree.Lambda expr, FuncVisitor mv) {

        var caught= expr.currSymbol.size();
        System.out.println("form tac");
        if (expr.required) {
            caught += 1;
        }
        var allocationOfNumber = mv.visitLoad(4 + caught * 4);
        var allocation = mv.visitIntrinsicCall(Intrinsic.ALLOCATE, true, allocationOfNumber);
        int offset = 4;
        if (expr.required) {
            if (!lambdaStack.isEmpty() && lambdaStack.peek().required){
                var thisTemp = mv.visitLoadFrom(mv.getArgTemp(0), 4);
                mv.visitStoreTo(allocation, offset, thisTemp);
                offset += 4;
            } else {
              System.out.println("form tac");
                var thisTemp = mv.getArgTemp(0);
                mv.visitStoreTo(allocation, offset, thisTemp);
                offset += 4;
            }
        }

        for (var catched: expr.currSymbol.entrySet()) {
            catched.setValue(offset);
            if (!lambdaStack.isEmpty() && lambdaStack.peek().currSymbol.keySet().contains(catched.getKey())){
                var temp = mv.visitLoadFrom(mv.getArgTemp(0), lambdaStack.peek().currSymbol.get(catched.getKey()));
                mv.visitStoreTo(allocation, offset, temp);
            } else {
                mv.visitStoreTo(allocation, offset, ((VarSymbol) catched.getKey()).temp);
            }
            offset += 4;
            System.out.println("form tac");
        }

        mv.getProgramWriter().addFuncLab("lambda", expr.lambdaSymbol.name);
        var newFunc = mv.getProgramWriter().visitFunc("lambda", expr.lambdaSymbol.name, expr.lambdaSymbol.funtype.arity() + 1);
        newFunc.isLambda = true;
        newFunc.Lambda = expr;
        lambdaStack.push(expr);
        var count = 1;
        for (var param : expr.varlist) {
            param.symbol.temp = newFunc.getArgTemp(count);
            count++;
        }
        expr.expr.accept(this, newFunc);
        if (expr.expr instanceof Tree.Expr){
            newFunc.visitReturn(((Tree.Expr) expr.expr).val);
        }
        newFunc.visitEnd();
        lambdaStack.pop();
        var vv = mv.visitLoadVTable(".globl");
        var FunctionPoint = mv.visitMemberAccess(vv, "lambda", expr.lambdaSymbol.name);
        mv.visitStoreTo(allocation, FunctionPoint);
        expr.val = allocation;
    }

    @Override
    default void visitClassTest(Tree.ClassTest expr, FuncVisitor mv) {
        // Accelerate: when obj.type <: class.type, then the test must be successful!
        if (expr.obj.type.subtypeOf(expr.symbol.type)) {
            expr.val = mv.visitLoad(1);
            return;
        }

        expr.obj.accept(this, mv);
        expr.val = emitClassTest(expr.obj.val, expr.symbol.name, mv);
    }

    @Override
    default void visitBlock(Tree.Block block, FuncVisitor mv) {
        for (var stmt : block.stmts) {
            stmt.accept(this, mv);
        }
        System.out.println("form tac");
    }

    @Override
    default void visitLocalVarDef(Tree.LocalVarDef def, FuncVisitor mv) {
        def.symbol.temp = mv.freshTemp();
        if (def.initVal.isEmpty()) return;
        var initVal = def.initVal.get();

        initVal.accept(this, mv);
        System.out.println("form tac");
        mv.visitAssign(def.symbol.temp, initVal.val);
    }



    @Override
    default void visitExprEval(Tree.ExprEval eval, FuncVisitor mv) {
        eval.expr.accept(this, mv);
    }

    @Override
    default void visitIf(Tree.If stmt, FuncVisitor mv) {
        stmt.cond.accept(this, mv);
        Consumer<FuncVisitor> trueBranch = v -> stmt.trueBranch.accept(this, v);

        if (stmt.falseBranch.isEmpty()) {
            emitIfThen(stmt.cond.val, trueBranch, mv);
        } else {
            Consumer<FuncVisitor> falseBranch = v -> stmt.falseBranch.get().accept(this, v);
            emitIfThenElse(stmt.cond.val, trueBranch, falseBranch, mv);
        }
    }

    @Override
    default void visitWhile(Tree.While loop, FuncVisitor mv) {
        var exit = mv.freshLabel();
        Function<FuncVisitor, Temp> test = v -> {
            loop.cond.accept(this, v);
            return loop.cond.val;
        };
        Consumer<FuncVisitor> body = v -> {
          System.out.println("form tac");
            loopExits.push(exit);
            loop.body.accept(this, v);
            loopExits.pop();
        };
        emitWhile(test, body, exit, mv);
    }

    @Override
    default void visitFor(Tree.For loop, FuncVisitor mv) {
        var exit = mv.freshLabel();
        loop.init.accept(this, mv);
        Function<FuncVisitor, Temp> test = v -> {
            loop.cond.accept(this, v);
            return loop.cond.val;
        };
        Consumer<FuncVisitor> body = v -> {
            loopExits.push(exit);
            loop.body.accept(this, v);
            System.out.println("form tac");
            loopExits.pop();
            loop.update.accept(this, v);
        };
        emitWhile(test, body, exit, mv);
    }

    @Override
    default void visitBreak(Tree.Break stmt, FuncVisitor mv) {
        mv.visitBranch(loopExits.peek());
    }

    @Override
    default void visitReturn(Tree.Return stmt, FuncVisitor mv) {
        if (stmt.expr.isEmpty()) {
            mv.visitReturn();
        } else {
            var expr = stmt.expr.get();
            expr.accept(this, mv);
            System.out.println("form tac");
            mv.visitReturn(expr.val);
        }
    }



    @Override
    default void visitPrint(Tree.Print stmt, FuncVisitor mv) {
        for (var expr : stmt.exprs) {
            expr.accept(this, mv);
            if (expr.type.eq(BuiltInType.INT)) {
                mv.visitIntrinsicCall(Intrinsic.PRINT_INT, expr.val);
            } else if (expr.type.eq(BuiltInType.BOOL)) {
                mv.visitIntrinsicCall(Intrinsic.PRINT_BOOL, expr.val);
            } else if (expr.type.eq(BuiltInType.STRING)) {
                mv.visitIntrinsicCall(Intrinsic.PRINT_STRING, expr.val);
            }
        }
    }

    // Expressions

    @Override
    default void visitIntLit(Tree.IntLit expr, FuncVisitor mv) {
      System.out.println("form tac");
        expr.val = mv.visitLoad(expr.value);
    }

    @Override
    default void visitBoolLit(Tree.BoolLit expr, FuncVisitor mv) {
        expr.val = mv.visitLoad(expr.value);
    }

    @Override
    default void visitStringLit(Tree.StringLit expr, FuncVisitor mv) {
        // Remember to unquote the string literal
        var unquoted = expr.value.substring(1, expr.value.length() - 1)
                .replaceAll("\\\\r", "\r")
                .replaceAll("\\\\n", "\n")
                .replaceAll("\\\\t", "\t")
                .replaceAll("\\\\\\\\", "\\")
                .replaceAll("\\\\\"", "\"");
        expr.val = mv.visitLoad(unquoted);
    }

    @Override
    default void visitNullLit(Tree.NullLit expr, FuncVisitor mv) {
        expr.val = mv.visitLoad(0);
    }

    @Override
    default void visitReadInt(Tree.ReadInt expr, FuncVisitor mv) {
        expr.val = mv.visitIntrinsicCall(Intrinsic.READ_INT, true);
    }

    @Override
    default void visitAssign(Tree.Assign assign, FuncVisitor mv) {
        if (assign.lhs instanceof Tree.IndexSel) {
            var indexSel = (Tree.IndexSel) assign.lhs;
            indexSel.array.accept(this, mv);
            indexSel.index.accept(this, mv);
            var addr = emitArrayElementAddress(indexSel.array.val, indexSel.index.val, mv);
            assign.rhs.accept(this, mv);
            mv.visitStoreTo(addr, assign.rhs.val);
        } else if (assign.lhs instanceof Tree.VarSel) {
          System.out.println("form tac");
            var v = (Tree.VarSel) assign.lhs;
            if(v.symbol.isVarSymbol()) {
                if (((VarSymbol)v.symbol).isMemberVar()) {
                    var ob = v.receiver.get();
                    ob.accept(this, mv);
                    assign.rhs.accept(this, mv);
                    mv.visitMemberWrite(ob.val, ((VarSymbol)v.symbol).getOwner().name, v.name, assign.rhs.val);
                } else {
                    assign.rhs.accept(this, mv);
                    mv.visitAssign(((VarSymbol)v.symbol).temp, assign.rhs.val);
                }
            }
        }
    }


    @Override
    default void visitReadLine(Tree.ReadLine expr, FuncVisitor mv) {
        expr.val = mv.visitIntrinsicCall(Intrinsic.READ_LINE, true);
    }

    @Override
    default void visitUnary(Tree.Unary expr, FuncVisitor mv) {
        var op = switch (expr.op) {
            case NEG -> TacInstr.Unary.Op.NEG;
            case NOT -> TacInstr.Unary.Op.LNOT;
        };

        expr.operand.accept(this, mv);
        expr.val = mv.visitUnary(op, expr.operand.val);
        System.out.println("form tac");
    }



    @Override
    default void visitIndexSel(Tree.IndexSel expr, FuncVisitor mv) {
        expr.array.accept(this, mv);
        expr.index.accept(this, mv);
        var addr = emitArrayElementAddress(expr.array.val, expr.index.val, mv);
        expr.val = mv.visitLoadFrom(addr);
    }

    @Override
    default void visitNewArray(Tree.NewArray expr, FuncVisitor mv) {
        expr.length.accept(this, mv);
        expr.val = emitArrayInit(expr.length.val, mv);
    }

    @Override
    default void visitNewClass(Tree.NewClass expr, FuncVisitor mv) {
        expr.val = mv.visitNewClass(expr.symbol.name);
    }

    @Override
    default void visitThis(Tree.This expr, FuncVisitor mv) {
        if (mv.isLambda){
            var ob = mv.getArgTemp(0);
            expr.val = mv.visitLoadFrom(ob,4);
        } else {
            expr.val = mv.getArgTemp(0);
            System.out.println("form tac");
        }

    }



    @Override
    default void visitCall(Tree.Call expr, FuncVisitor mv) {
        if (expr.isArrayLength) {
            var array = expr.receiver.get();
            array.accept(this, mv);
            expr.val = mv.visitLoadFrom(array.val, -4);
        } else {

            expr.args.forEach(arg -> arg.accept(this, mv));
            var temps = new ArrayList<Temp>();
            expr.args.forEach(arg -> temps.add(arg.val));
            expr.receiver.get().accept(this, mv);
            // System.out.println(expr);
            if(expr.symbol.isMethodSymbol() && expr.receiver.get() instanceof Tree.VarSel) {
                if(((MethodSymbol)expr.symbol).isStatic()) {
                    if(((MethodSymbol)expr.symbol).type.returnType.isVoidType()) {
                        mv.visitStaticCall(((MethodSymbol)expr.symbol).owner.name, expr.symbol.name, temps);
                    } else {
                        expr.val = mv.visitStaticCall(((MethodSymbol)expr.symbol).owner.name, expr.symbol.name, temps, true);
                    }
                } else {
                    var ob = ((Tree.VarSel)expr.receiver.get()).receiver.get();
                    if (((MethodSymbol)expr.symbol).type.returnType.isVoidType()) {
                        mv.visitMemberCall(ob.val, ((MethodSymbol)expr.symbol).owner.name, expr.symbol.name, temps);
                    } else {
                        expr.val = mv.visitMemberCall(ob.val, ((MethodSymbol)expr.symbol).owner.name, expr.symbol.name, temps, true);
                    }
                }

            } else {
                FunType fun;
                if (expr.symbol.isVarSymbol()) {
                    fun = (FunType)((VarSymbol)expr.symbol).type;
                } else if (expr.symbol.isLambdaSymbol()) {
                    fun = ((LambdaSymbol) expr.symbol).funtype;
                } else {
                    fun = ((MethodSymbol) expr.symbol).type;
                }
                if (fun.returnType.isVoidType()) {
                    mv.visitFuncCall(expr.receiver.get().val, expr.symbol.name, temps);
                } else {
                    expr.val = mv.visitFuncCall(expr.receiver.get().val, expr.symbol.name, temps, true);
                }
            }
        }
    }







    @Override
    default void visitClassCast(Tree.ClassCast expr, FuncVisitor mv) {
        expr.obj.accept(this, mv);
        expr.val = expr.obj.val;

        // Accelerate: when obj.type <: class.type, then the test must success!
        if (expr.obj.type.subtypeOf(expr.symbol.type)) {
            return;
        }
        var result = emitClassTest(expr.obj.val, expr.symbol.name, mv);

        var exit = mv.freshLabel();
        mv.visitBranch(TacInstr.CondBranch.Op.BNEZ, result, exit);
        mv.visitPrint(RuntimeError.CLASS_CAST_ERROR1);
        var vtbl1 = mv.visitLoadFrom(expr.obj.val);
        var fromClass = mv.visitLoadFrom(vtbl1, 4);
        mv.visitIntrinsicCall(Intrinsic.PRINT_STRING, fromClass);
        mv.visitPrint(RuntimeError.CLASS_CAST_ERROR2);
        var vtbl2 = mv.visitLoadVTable(expr.symbol.name);
        var toClass = mv.visitLoadFrom(vtbl2, 4);
        mv.visitIntrinsicCall(Intrinsic.PRINT_STRING, toClass);
        mv.visitPrint(RuntimeError.CLASS_CAST_ERROR3);
        mv.visitIntrinsicCall(Intrinsic.HALT);
        mv.visitLabel(exit);
    }

    private void emitIfThen(Temp cond, Consumer<FuncVisitor> action, FuncVisitor mv) {
        var skip = mv.freshLabel();
        mv.visitBranch(TacInstr.CondBranch.Op.BEQZ, cond, skip);
        action.accept(mv);
        mv.visitLabel(skip);
    }

    private void emitIfThenElse(Temp cond, Consumer<FuncVisitor> trueBranch, Consumer<FuncVisitor> falseBranch,
                                FuncVisitor mv) {
        var skip = mv.freshLabel();
        var exit = mv.freshLabel();
        mv.visitBranch(TacInstr.CondBranch.Op.BEQZ, cond, skip);
        trueBranch.accept(mv);
        mv.visitBranch(exit);
        mv.visitLabel(skip);
        falseBranch.accept(mv);
        mv.visitLabel(exit);
    }

    @Override
    default void visitBinary(Tree.Binary expr, FuncVisitor mv) {
        if ((expr.op.equals(Tree.BinaryOp.EQ) || expr.op.equals(Tree.BinaryOp.NE)) &&
                expr.lhs.type.eq(BuiltInType.STRING)) { // string eq/ne
            expr.lhs.accept(this, mv);
            expr.rhs.accept(this, mv);
            expr.val = mv.visitIntrinsicCall(Intrinsic.STRING_EQUAL, true, expr.lhs.val, expr.rhs.val);
            if (expr.op.equals(Tree.BinaryOp.NE)) {
                mv.visitUnarySelf(TacInstr.Unary.Op.LNOT, expr.val);
            }
            return;
        }

        var op = switch (expr.op) {
            case ADD -> TacInstr.Binary.Op.ADD;
            case SUB -> TacInstr.Binary.Op.SUB;
            case MUL -> TacInstr.Binary.Op.MUL;
            case DIV -> TacInstr.Binary.Op.DIV;
            case MOD -> TacInstr.Binary.Op.MOD;
            case EQ -> TacInstr.Binary.Op.EQU;
            case NE -> TacInstr.Binary.Op.NEQ;
            case LT -> TacInstr.Binary.Op.LES;
            case LE -> TacInstr.Binary.Op.LEQ;
            case GT -> TacInstr.Binary.Op.GTR;
            case GE -> TacInstr.Binary.Op.GEQ;
            case AND -> TacInstr.Binary.Op.LAND;
            case OR -> TacInstr.Binary.Op.LOR;
        };
        expr.lhs.accept(this, mv);
        expr.rhs.accept(this, mv);
        if(expr.op.equals(Tree.BinaryOp.DIV) || expr.op.equals(Tree.BinaryOp.MOD)) {
            var z = mv.visitLoad(0);
            var err = mv.visitBinary(TacInstr.Binary.Op.EQU, expr.rhs.val, z);
            var h = new Consumer<FuncVisitor>() {
                @Override
                public void accept(FuncVisitor v){
                    v.visitPrint(RuntimeError.CLASS_DIVIDED_BY_0);
                    v.visitIntrinsicCall(Intrinsic.HALT);
                }
            };
            emitIfThen(err, h, mv);
        }
        expr.val = mv.visitBinary(op, expr.lhs.val, expr.rhs.val);
    }

    private void emitWhile(Function<FuncVisitor, Temp> test, Consumer<FuncVisitor> block,
                           Label exit, FuncVisitor mv) {
        var entry = mv.freshLabel();
        mv.visitLabel(entry);
        var cond = test.apply(mv);
        mv.visitBranch(TacInstr.CondBranch.Op.BEQZ, cond, exit);
        block.accept(mv);
        mv.visitBranch(entry);
        mv.visitLabel(exit);
    }

    private Temp emitArrayInit(Temp length, FuncVisitor mv) {
        var zero = mv.visitLoad(0);
        var error = mv.visitBinary(TacInstr.Binary.Op.LES, length, zero);
        var handler = new Consumer<FuncVisitor>() {
            @Override
            public void accept(FuncVisitor v) {
                v.visitPrint(RuntimeError.NEGATIVE_ARR_SIZE);
                System.out.println("form tac");
                v.visitIntrinsicCall(Intrinsic.HALT);
            }
        };
        emitIfThen(error, handler, mv);

        var units = mv.visitBinary(TacInstr.Binary.Op.ADD, length, mv.visitLoad(1));
        var four = mv.visitLoad(4);
        var size = mv.visitBinary(TacInstr.Binary.Op.MUL, units, four);
        var a = mv.visitIntrinsicCall(Intrinsic.ALLOCATE, true, size);
        mv.visitStoreTo(a, length);
        var p = mv.visitBinary(TacInstr.Binary.Op.ADD, a, size);
        mv.visitBinarySelf(TacInstr.Binary.Op.SUB, p, four);
        Function<FuncVisitor, Temp> test = v -> v.visitBinary(TacInstr.Binary.Op.NEQ, p, a);
        var body = new Consumer<FuncVisitor>() {
            @Override
            public void accept(FuncVisitor v) {
                v.visitStoreTo(p, zero);
                v.visitBinarySelf(TacInstr.Binary.Op.SUB, p, four);
            }
        };
        emitWhile(test, body, mv.freshLabel(), mv);
        return mv.visitBinary(TacInstr.Binary.Op.ADD, a, four);
    }

    private Temp emitArrayElementAddress(Temp array, Temp index, FuncVisitor mv) {
        var length = mv.visitLoadFrom(array, -4);
        var zero = mv.visitLoad(0);
        var error1 = mv.visitBinary(TacInstr.Binary.Op.LES, index, zero);
        var error2 = mv.visitBinary(TacInstr.Binary.Op.GEQ, index, length);
        var error = mv.visitBinary(TacInstr.Binary.Op.LOR, error1, error2);
        var handler = new Consumer<FuncVisitor>() {
            @Override
            public void accept(FuncVisitor v) {
              System.out.println("form tac");
                v.visitPrint(RuntimeError.ARRAY_INDEX_OUT_OF_BOUND);
                v.visitIntrinsicCall(Intrinsic.HALT);
            }
        };
        emitIfThen(error, handler, mv);

        var four = mv.visitLoad(4);
        var offset = mv.visitBinary(TacInstr.Binary.Op.MUL, index, four);
        return mv.visitBinary(TacInstr.Binary.Op.ADD, array, offset);
    }

    private Temp emitClassTest(Temp object, String clazz, FuncVisitor mv) {
        var target = mv.visitLoadVTable(clazz);
        var t = mv.visitLoadFrom(object);

        var loop = mv.freshLabel();
        var exit = mv.freshLabel();
        mv.visitLabel(loop);
        var ret = mv.visitBinary(TacInstr.Binary.Op.EQU, t, target);
        mv.visitBranch(TacInstr.CondBranch.Op.BNEZ, ret, exit);
        mv.visitRaw(new TacInstr.Memory(TacInstr.Memory.Op.LOAD, t, t, 0));
        mv.visitBranch(TacInstr.CondBranch.Op.BNEZ, t, loop);
        var zero = mv.visitLoad(0);
        mv.visitAssign(ret, zero);
        mv.visitLabel(exit);

        return ret;
    }

    @Override
    default void visitVarSel(Tree.VarSel expr, FuncVisitor mv) {
        if(!lambdaStack.isEmpty()) {
            var m = lambdaStack.peek();
            if(expr.symbol.isVarSymbol() && ((VarSymbol)expr.symbol).isMemberVar() ) {
                var ob = mv.visitLoadFrom(mv.getArgTemp(0), 4);
                expr.val = mv.visitMemberAccess(ob, ((VarSymbol)expr.symbol).getOwner().name, expr.name);
                return;
            } else if(m.currSymbol.containsKey(expr.symbol)) {
                expr.val = mv.visitLoadFrom(mv.getArgTemp(0), m.currSymbol.get(expr.symbol));
                return;
            }
        }

        if(expr.symbol.isVarSymbol()) {
            if(((VarSymbol)expr.symbol).isMemberVar()) {
                var ob = expr.receiver.get();
                ob.accept(this, mv);
                expr.val = mv.visitMemberAccess(ob.val, ((VarSymbol)expr.symbol).getOwner().name, expr.name);
            } else {
                expr.val = ((VarSymbol)expr.symbol).temp;
                System.out.println("form tac");
            }
        } else if(expr.symbol.isMethodSymbol()) {
            var methods = ((MethodSymbol)expr.symbol);
            if(methods.isStatic()) {

                mv.getProgramWriter().addFuncLab(".globl::" + methods.getOwner().name, methods.name);
                var newFunc = mv.getProgramWriter().visitFunc(".globl::" + methods.getOwner().name, methods.name, methods.type.arity() + 1);
                newFunc.visitVarSelStatic(methods.getOwner().name, methods.name, !methods.type.returnType.isVoidType(), methods.type.arity() + 1);
                newFunc.visitEnd();
                var addr = mv.visitIntrinsicCall(Intrinsic.ALLOCATE,true, mv.visitLoad(4));
                var vt = mv.visitLoadVTable(".globl");
                var funcVal = mv.visitMemberAccess(vt, ".globl::" + methods.getOwner().name, methods.name);
                mv.visitStoreTo(addr, funcVal);
                expr.val = addr;
                System.out.println("form tac");
            } else {

                var ob = expr.receiver.get();
                ob.accept(this,mv);
                mv.getProgramWriter().addFuncLab(".globl::" + methods.getOwner().name, methods.name);
                var newFunc = mv.getProgramWriter().visitFunc(".globl::" + methods.getOwner().name, methods.name, methods.type.arity() + 1);
                newFunc.visitVarSel(methods.getOwner().name,methods.name, methods.type.returnType != null, methods.type.arity() + 1);
                newFunc.visitEnd();
                var alloc = mv.visitIntrinsicCall(Intrinsic.ALLOCATE, true, mv.visitLoad(8));
                var vv = mv.visitLoadVTable(".globl");
                var FunctionPoint = mv.visitMemberAccess(vv, ".globl::" + methods.getOwner().name, methods.name);
                mv.visitStoreTo(alloc, FunctionPoint);
                mv.visitStoreTo(alloc, 4, ob.val);
                expr.val = alloc;
            }
        }
    }

}
