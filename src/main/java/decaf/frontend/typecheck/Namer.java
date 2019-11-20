package decaf.frontend.typecheck;

import decaf.driver.Config;
import decaf.driver.Phase;
import decaf.driver.error.*;
import decaf.frontend.scope.*;
import decaf.frontend.symbol.*;
import decaf.frontend.tree.*;
import decaf.frontend.type.*;

import java.util.*;

/**
 * The namer phase: resolve all symbols defined in the abstract syntax tree and store them in symbol tables (i.e.
 * scopes).
 */
public class Namer extends Phase<Tree.TopLevel, Tree.TopLevel> implements TypeLitVisited {

    public Namer(Config config) {
        super("namer", config);
    }

    @Override
    public Tree.TopLevel transform(Tree.TopLevel tree) {
        tree.globalScope = new GlobalScope();
        var ctx = new ScopeStack(tree.globalScope);
        tree.accept(this, ctx);
        return tree;
    }

    @Override
    public void visitTopLevel(Tree.TopLevel program, ScopeStack ctx) {
        System.out.println("Namer visitTopLevel");
        var classes = new TreeMap<String, Tree.ClassDef>();

        // Check conflicting definitions. If any, ignore the redefined ones.
        for (var clazz : program.classes) {
            var earlier = classes.get(clazz.name);
            if (earlier != null) {
                issue(new DeclConflictError(clazz.pos, clazz.name, earlier.pos));
            } else {
                classes.put(clazz.name, clazz);
            }
        }

        // Make sure the base class exists. If not, ignore the inheritance.
        for (var clazz : classes.values()) {
            clazz.parent.ifPresent(p -> {
                if (classes.containsKey(p.name)) { // good
                    clazz.superClass = classes.get(p.name);
                } else { // bad
                    issue(new ClassNotFoundError(clazz.pos, p.name));
                    clazz.parent = Optional.empty();
                }
            });
        }

        // Make sure any inheritance does not form a cycle.
        checkCycles(classes);
        // If so, return with errors.
        if (hasError()) return;

        // So far, class inheritance is well-formed, i.e. inheritance relations form a forest of trees. Now we need to
        // resolve every class definition, make sure that every member (variable/method) is well-typed.
        // Realizing that a class type can be used in the definition of a class member, either a variable or a method,
        // we shall first know all the accessible class types in the program. These types are wrapped into
        // `ClassSymbol`s. Note that currently, the associated `scope` is empty because member resolving has not
        // started yet. All class symbols are stored in the global scope.
        for (var clazz : classes.values()) {
            createClassSymbol(clazz, ctx.global);
        }

        // Now, we can resolve every class definition to fill in its class scope table. To check if the overriding
        // behaves correctly, we should first resolve super class and then its subclasses.
        for (var clazz : classes.values()) {
            clazz.accept(this, ctx);
            if (!clazz.symbol.isAbstract() && !clazz.symbol.abstractMethods.isEmpty()){
                issue(new MyAbstractError1(clazz.symbol.pos, clazz.symbol.name));
            }
        }

        // Finally, let's locate the main class, whose name is 'Main', and contains a method like:
        //  static void main() { ... }
        boolean found = false;
        for (var clazz : classes.values()) {
            if (!clazz.symbol.isAbstract() && clazz.name.equals("Main")) {
                var symbol = clazz.symbol.scope.find("main");
                if (symbol.isPresent() && symbol.get().isMethodSymbol()) {
                    var method = (MethodSymbol) symbol.get();
                    if (method.isStatic() && method.type.returnType.isVoidType() && method.type.arity() == 0) {
                        method.setMain();
                        program.mainClass = clazz.symbol;
                        clazz.symbol.setMainClass();
                        found = true;
                    }
                }
            }
        }
        if (!found) {
            issue(new NoMainClassError());
        }
    }

    /**
     * Check if class inheritance form cycle(s).
     *
     * @param classes a map between class names to their definitions
     */
    private void checkCycles(Map<String, Tree.ClassDef> classes) {
        var visitedTime = new TreeMap<String, Integer>();
        for (var clazz : classes.values()) {
            visitedTime.put(clazz.name, 0);
        }

        var time = 1; // nodes in the same inheritance path/chain have the same time
        Tree.ClassDef from = null;
        for (var node : classes.keySet()) {
            if (visitedTime.get(node) != 0) { // already done, skip
                continue;
            }

            // visit from this node
            while (true) {
                if (visitedTime.get(node) == 0) { // not visited yet
                    visitedTime.put(node, time);
                    var clazz = classes.get(node);
                    if (clazz.parent.isPresent()) {
                        // continue to visit its parent
                        node = clazz.parent.get().name;
                        from = clazz;
                    } else break;
                } else if (visitedTime.get(node) == time) { // find a cycle
                    issue(new BadInheritanceError(from.pos));
                    break;
                } else { // this node is visited earlier, also done
                    break;
                }
            }
            time++;
        }
    }

    /**
     * Create a class symbol and declare in the global scope.
     *
     * @param clazz  class definition
     * @param global global scope
     */
    private void createClassSymbol(Tree.ClassDef clazz, GlobalScope global) {
        if (global.containsKey(clazz.name)) return;

        if (clazz.parent.isPresent()) {
            createClassSymbol(clazz.superClass, global);
            var base = global.getClass(clazz.parent.get().name);
            var type = new ClassType(clazz.name, base.type);
            var scope = new ClassScope(base.scope);
            var symbol = new ClassSymbol(clazz.name, base, type, scope, clazz.pos, clazz.modifiers);
            System.out.println("Namer Created class symbol "+clazz.name + " with parent " + clazz.parent.get().name);
            global.declare(symbol);
            clazz.symbol = symbol;
        } else {
            var type = new ClassType(clazz.name);
            var scope = new ClassScope();
            var symbol = new ClassSymbol(clazz.name, type, scope, clazz.pos, clazz.modifiers);
            System.out.println("Namer Created class symbol "+clazz.name + " with no parent");
            global.declare(symbol);
            clazz.symbol = symbol;
        }
    }

    @Override
    public void visitClassDef(Tree.ClassDef clazz, ScopeStack ctx) {
        System.out.println("Namer visitClassDef "+clazz.name);
        if (clazz.resolved) return;

        if (clazz.hasParent()) {
            clazz.superClass.accept(this, ctx);
            clazz.symbol.abstractMethods.addAll(clazz.superClass.symbol.abstractMethods);
        }

        ctx.open(clazz.symbol.scope);
        for (var field : clazz.fields) {
            field.accept(this, ctx);
        }
        ctx.close();
        clazz.resolved = true;
    }

    @Override
    public void visitVarDef(Tree.VarDef varDef, ScopeStack ctx) {
        System.out.println("Namer visitVarDef " + varDef.name);
        varDef.typeLit.accept(this, ctx);
        var earlier = ctx.findConflict(varDef.name);
        if (earlier.isPresent()) {
            if (earlier.get().isVarSymbol() && earlier.get().domain() != ctx.currentScope()) {
                issue(new OverridingVarError(varDef.pos, varDef.name));
            } else {
                issue(new DeclConflictError(varDef.pos, varDef.name, earlier.get().pos));
            }
            return;
        }

        if (varDef.typeLit.type.eq(BuiltInType.VOID)) {
            issue(new BadVarTypeError(varDef.pos, varDef.name));
            return;
        }

        if (varDef.typeLit.type.noError()) {
            var symbol = new VarSymbol(varDef.name, varDef.typeLit.type, varDef.pos);
            ctx.declare(symbol);
            varDef.symbol = symbol;
        }
    }

    @Override
    public void visitMethodDef(Tree.MethodDef method, ScopeStack ctx) {
        System.out.println("Namer visitMethodDef "+method.name);
        var earlier = ctx.findConflict(method.name);
        if (earlier.isPresent()) {//命名有冲突
            System.out.println("Namer visitMethodDef - 命名有冲突");
            if (earlier.get().isMethodSymbol() && earlier.get().domain() != ctx.currentScope()) { //与另一个定义域内函数名冲突
                var suspect = (MethodSymbol) earlier.get();
                if (suspect.isAbstract() && method.isAbstract()) { //两个都是抽象函数
                    System.out.println("Namer visitMethodDef - 两个都是抽象函数");
                    var formal = new FormalScope();
                    typeMethod(method, ctx, formal);
                    if (method.type.subtypeOf(suspect.type)){ //类型正确
                        var symbol = new MethodSymbol(method.name, method.type, formal, method.pos, method.modifiers, ctx.currentClass());
                        ctx.declare(symbol);
                        method.symbol = symbol;
                    } else { //类型不正确
                        issue(new BadOverrideError(method.pos, method.name, suspect.owner.name));
                    }
                } else if (suspect.isAbstract() && !method.isAbstract() && !method.isStatic()) { //前一个抽象 后一个正常
                    System.out.println("Namer visitMethodDef - 前一个抽象 后一个正常");
                    var formal = new FormalScope();
                    typeMethod(method, ctx, formal);
                    if (method.type.subtypeOf(suspect.type)) { // 类型正确
                        System.out.println("Namer visitMethodDef - method.type is "+method.type);
                        System.out.println("Namer visitMethodDef - suspect.type is "+suspect.type);
                        var symbol = new MethodSymbol(method.name, method.type, formal, method.pos, method.modifiers, ctx.currentClass());
                        ctx.declare(symbol);
                        method.symbol = symbol;
                        ctx.open(formal);
                        method.body.accept(this, ctx);
                        ctx.close();
                        ctx.currentClass().abstractMethods.remove(method.name);
                        System.out.println("Namer visitMethodDef - " + ctx.currentClass().name + " remove " + method.name + " . Now length is " + ctx.currentClass().abstractMethods.size());
                    } else { //类型不正确
                        issue(new BadOverrideError(method.pos, method.name, suspect.owner.name));
                    }
                } else if (!suspect.isAbstract() && !suspect.isStatic() && !method.isAbstract() && !method.isStatic()){ //两个都是正常函数
                    System.out.println("Namer visitMethodDef - 两个都是正常函数");
                    var formal = new FormalScope();
                    typeMethod(method, ctx, formal);
                    if (method.type.subtypeOf(suspect.type)) { // 类型正确
                        var symbol = new MethodSymbol(method.name, method.type, formal, method.pos, method.modifiers, ctx.currentClass());
                        ctx.declare(symbol);
                        method.symbol = symbol;
                        ctx.open(formal);
                        method.body.accept(this, ctx);
                        ctx.close();
                    } else { //参数类型不正确
                        issue(new BadOverrideError(method.pos, method.name, suspect.owner.name));
                    }
                } else { //非以上列出的情况
                    System.out.println("Namer visitMethodDef - 非列出情况");
                    issue(new DeclConflictError(method.pos, method.name, suspect.pos));
                }
            } else { //与此定义域的函数名或者是任何定义域非函数名冲突
                System.out.println("Namer visitMethodDef - 与此定义域的函数名或者是任何定义域非函数名冲突");
                issue(new DeclConflictError(method.pos, method.name, earlier.get().pos));
            }
        } else { //命名无冲突 说明当前是新的函数
            System.out.println("Namer visitMethodDef - 命名无冲突");
            var formal = new FormalScope();//新建空白参数作用域
            typeMethod(method, ctx, formal);//参数作用域中建立this变量 构造好函数类型
            var symbol = new MethodSymbol(method.name, method.type, formal, method.pos, method.modifiers, ctx.currentClass());
            ctx.declare(symbol);
            method.symbol = symbol;
            if (method.isAbstract()) { //当前新函数为抽象函数
                ctx.currentClass().abstractMethods.add(method.name);
                System.out.println("Namer visitMethodDef - "+ctx.currentClass().name + " add " + method.name + " . Now length is " + ctx.currentClass().abstractMethods.size());
            } else { //当前新函数不是抽象函数
                ctx.open(formal);
                method.body.accept(this, ctx);
                ctx.close();
            }
        }
    }

    private void typeMethod(Tree.MethodDef method, ScopeStack ctx, FormalScope formal) {
        System.out.println("Namer typeMethod " + method.name);
        method.returnType.accept(this, ctx);
        ctx.open(formal);
        if (!method.isStatic()) ctx.declare(VarSymbol.thisVar(ctx.currentClass().type, method.id.pos));//新建this变量符号
        var argTypes = new ArrayList<Type>();
        for (var param : method.params) {
            param.accept(this, ctx);
            argTypes.add(param.typeLit.type);
        }
        method.type = new FunType(method.returnType.type, argTypes);//函数的类型构建
        ctx.close();
    }

    @Override
    public void visitLambda(Tree.Lambda lambda, ScopeStack ctx){
        System.out.println("Namer visitLambda lambda@"+lambda.pos);
        if (lambda.expr != null) {
            var formalScope = new LambdaFormalScope(ctx.currentScope());
            typeLambda(lambda, ctx, formalScope);
            var localScope = new LocalScope(formalScope);
            var symbol = new LambdaSymbol("lambda@"+lambda.pos, (FunType)lambda.type, lambda.pos, formalScope);
            ctx.declare(symbol);
            lambda.symbol = symbol;
            ctx.open(formalScope);
            ctx.open(localScope);
            lambda.expr.accept(this, ctx);
            ctx.close();
            ctx.close();
        } else if (lambda.body != null) {
            var formalScope = new LambdaFormalScope(ctx.currentScope());
            typeLambda(lambda, ctx, formalScope);
            var localScope = new LocalScope(formalScope);
            var symbol = new LambdaSymbol("lambda@"+lambda.pos, (FunType)lambda.type, lambda.pos, formalScope);
            ctx.declare(symbol);
            lambda.symbol = symbol;
            ctx.open(formalScope);
            lambda.body.accept(this, ctx);
            ctx.close();
        }
    }

    private void typeLambda(Tree.Lambda lambda, ScopeStack ctx, LambdaFormalScope formalScope) {
        ctx.open(formalScope);
        var argTypes = new ArrayList<Type>();
        for (var param : lambda.params) {
            param.accept(this, ctx);
            argTypes.add(param.typeLit.type);
        }
        lambda.type = new FunType(BuiltInType.WAIT, argTypes);
        ctx.close();
    }


    @Override
    public void visitBlock(Tree.Block block, ScopeStack ctx) {
        System.out.println("Namer visitBlock");

        block.scope = new LocalScope(ctx.currentScope());
        ctx.open(block.scope);
        for (var stmt : block.stmts) {
            stmt.accept(this, ctx);
        }
        ctx.close();
    }

    @Override
    public void visitLocalVarDef(Tree.LocalVarDef def, ScopeStack ctx) {
        System.out.println("Namer visitLocalVarDef " + def.name);
        if (def.typeLit == null) { // var 类型出现
            System.out.println("Namer visitLocalVarDef - var");
            var earlier = ctx.findConflict(def.name);
            if (earlier.isPresent()) { //命名冲突
                System.out.println("Namer visitLocalVarDef - 命名有冲突");
                issue(new DeclConflictError(def.pos, def.name, earlier.get().pos));
                assert(!def.initVal.isEmpty());//var类型等号后面不能为空
                var initVal = def.initVal.get();
                initVal.accept(this, ctx);
                return;
            } else { //命名无冲突
                System.out.println("Namer visitLocalVarDef - 命名无冲突");
                var symbol = new VarSymbol(def.name, BuiltInType.WAIT, def.id.pos);
                ctx.declare(symbol);
                def.symbol = symbol;
                assert(!def.initVal.isEmpty());//var类型等号后面不能为空
                var initVal = def.initVal.get();
                initVal.accept(this, ctx);
            }
        } else { // 不是 var 类型
            System.out.println("Namer visitLocalVarDef - not var");
            def.typeLit.accept(this, ctx);

            var earlier = ctx.findConflict(def.name);
            if (earlier.isPresent()) {
                System.out.println("Namer visitLocalVarDef - 命名有冲突");
                issue(new DeclConflictError(def.pos, def.name, earlier.get().pos));
                if (!def.initVal.isEmpty()) {
                    var initVal = def.initVal.get();
                    initVal.accept(this, ctx);
                }
                return;
            }
            System.out.println("Namer visitLocalVarDef - 命名无冲突");
            if (def.typeLit.type.eq(BuiltInType.VOID)) {
                issue(new BadVarTypeError(def.pos, def.name));
                if (!def.initVal.isEmpty()) {
                    var initVal = def.initVal.get();
                    initVal.accept(this, ctx);
                }
                return;
            }

            if (def.typeLit.type.noError()) {
                var symbol = new VarSymbol(def.name, def.typeLit.type, def.id.pos);
                ctx.declare(symbol);
                def.symbol = symbol;
                if (!def.initVal.isEmpty()) {
                    var initVal = def.initVal.get();
                    initVal.accept(this, ctx);
                }
            }
        }
    }
    @Override
    public void visitCall(Tree.Call expr, ScopeStack ctx){
        System.out.println("Namer visitCall " + expr.pos);
        expr.receiver.get().accept(this, ctx);
        for (Tree.Expr arg : expr.args) {
            arg.accept(this, ctx);
        }
    }

    @Override
    public void visitBinary(Tree.Binary expr, ScopeStack ctx) {
        System.out.println("Namer visitBinary");
        expr.lhs.accept(this, ctx);
        expr.rhs.accept(this, ctx);
    }

    @Override
    public void visitReturn(Tree.Return ret, ScopeStack ctx) {
        System.out.println("Namer visitReturn");
        if (ret.expr.isPresent()) ret.expr.get().accept(this, ctx);
    }

    @Override
    public void visitNewArray(Tree.NewArray expr, ScopeStack ctx) {
        System.out.println("Namer visitNewArray");
        expr.length.accept(this, ctx);
    }

    @Override
    public void visitAssign(Tree.Assign expr, ScopeStack ctx) {
        System.out.println("Namer visitAssign");
        expr.lhs.accept(this, ctx);
        expr.rhs.accept(this, ctx);
    }

    // @Override
    // public void visitVarSel(Tree.VarSel expr, ScopeStack ctx) {
    //     System.out.println("Namer visitVarSel");
    //     expr.receiver.get().accept(this, ctx);
    // }

    @Override
    public void visitIndexSel(Tree.IndexSel expr, ScopeStack ctx) {
        System.out.println("Namer visitIndexSel");
        expr.array.accept(this, ctx);
        expr.index.accept(this, ctx);
    }

    @Override
    public void visitFor(Tree.For loop, ScopeStack ctx) {
        System.out.println("Namer visitFor");

        loop.scope = new LocalScope(ctx.currentScope());
        ctx.open(loop.scope);
        loop.init.accept(this, ctx);
        for (var stmt : loop.body.stmts) {
            stmt.accept(this, ctx);
        }
        ctx.close();
    }

    @Override
    public void visitExprEval(Tree.ExprEval exprEval, ScopeStack ctx) {
        System.out.println("Namer visitExprEval" + exprEval.pos);
        exprEval.expr.accept(this, ctx);
    }

    @Override
    public void visitIf(Tree.If stmt, ScopeStack ctx) {
        System.out.println("Namer visitIf");

        stmt.trueBranch.accept(this, ctx);
        stmt.falseBranch.ifPresent(b -> b.accept(this, ctx));
    }

    @Override
    public void visitWhile(Tree.While loop, ScopeStack ctx) {
        System.out.println("Namer visitWhile");

        loop.body.accept(this, ctx);
    }

}
