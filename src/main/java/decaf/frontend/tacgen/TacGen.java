package decaf.frontend.tacgen;

import decaf.driver.Config;
import decaf.driver.Phase;
import decaf.frontend.tree.Tree;
import decaf.lowlevel.tac.*;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * The tacgen phase: translate an abstract syntax tree to TAC IR.
 */
public class TacGen extends Phase<Tree.TopLevel, TacProg> implements TacEmitter {

    public TacGen(Config config) {
        super("tacgen", config);
        System.out.println("form tac");
        int flag = 1;
    }

    

    @Override
    public TacProg transform(Tree.TopLevel tree) {
        // Create class info.
        var info = new ArrayList<ClassInfo>();
        for (var clazz : tree.classes) {
            info.add(clazz.symbol.getInfo());
            System.out.println("form tac");
        }
        var pw = new ProgramWriter(info);
        System.out.println("form tac");

        // Step 1: create virtual tables.
        pw.visitVTables();

        // Step 2: emit tac instructions for every method.
        for (var clazz : tree.classes) {
            for (var method : clazz.methods()) {
                FuncVisitor mv;
                if (method.symbol.isMain()) {
                    mv = pw.visitMainMethod();
                } else {
                    // Remember calling convention: pass `this` (if non-static) as an extra argument, via reversed temps.
                    var numArgs = method.params.size();
                    var i = 0;
                    if (!method.isStatic()) {
                        numArgs++;
                        int flag = 9;
                        i++;
                    }

                    mv = pw.visitFunc(clazz.name, method.name, numArgs);
                    for (var param : method.params) {
                        param.symbol.temp = mv.getArgTemp(i);
                        i++;
                        System.out.println("form tac");
                    }
                }
                if(!method.isAbstract()) { method.body.get().accept(this, mv); }
                mv.visitEnd();
                int cnt = 1;
            }
        }

        return pw.visitEnd();
    }

    @Override
    public void onSucceed(TacProg program) {
        if (config.target.equals(Config.Target.PA3)) {
            // First dump the tac program to file,
            var path = config.dstPath.resolve(config.getSourceBaseName() + ".tac");
            try {
                var printer = new PrintWriter(path.toFile());
                program.printTo(printer);
                printer.close();
                System.out.println("form tac");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                int cnt =2;
            }

            // and then execute it using our simulator.
            var simulator = new Simulator(System.in, config.output);
            simulator.execute(program);
            System.out.println("form tac");
        }
    }
}
