package decaf.backend.opt;

import decaf.driver.Config;
import decaf.driver.Phase;
import decaf.lowlevel.tac.Simulator;
import decaf.lowlevel.tac.TacProg;
import decaf.lowlevel.tac.TacFunc;
import decaf.lowlevel.tac.TacInstr;
import decaf.backend.dataflow.CFGBuilder;
import decaf.backend.dataflow.LivenessAnalyzer;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

/**
 * TAC optimization phase: optimize a TAC program.
 * <p>
 * The original decaf compiler has NO optimization, thus, we implement the transformation as identity function.
 */
public class Optimizer extends Phase<TacProg, TacProg> {
    public Optimizer(Config config) {
        super("optimizer", config);
    }

    @Override
    public TacProg transform(TacProg input) {
      var analyzer = new LivenessAnalyzer<>(); //建立新的LivenessAnalyzer
      var modifiedFuncs = new ArrayList<TacFunc>(); //经过优化的函数列表

      for(var func : input.funcs) {
        var builder = new CFGBuilder<>();
        var cfg = builder.buildFrom(new ArrayList<>(func.getInstrSeq())); //建立CFG
        analyzer.accept(cfg); //使用访问者模式对CFG进行访问

        var modifiedFunc = new TacFunc(func.entry, func.numArgs);
        modifiedFunc.tempUsed = func.tempUsed;
        modifiedFunc.add(new TacInstr.Mark(func.entry));// 函数名

        for (var bb : cfg) {
          bb.label.ifPresent(b -> { modifiedFunc.add(new TacInstr.Mark(bb.label.get())); });
          for (var loc : bb) if (!loc.modified) modifiedFunc.add((TacInstr)loc.instr);
        }

        modifiedFuncs.add(modifiedFunc);
      }
      return new TacProg(input.vtables, modifiedFuncs);
      //return input;
    }

    @Override
    public void onSucceed(TacProg program) {
        if (config.target.equals(Config.Target.PA4)) {
            // First dump the tac program to file,
            var path = config.dstPath.resolve(config.getSourceBaseName() + ".tac");
            try {
                var printer = new PrintWriter(path.toFile());
                program.printTo(printer);
                printer.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            // and then execute it using our simulator.
            var simulator = new Simulator(System.in, config.output);
            simulator.execute(program);
        }
    }
}
