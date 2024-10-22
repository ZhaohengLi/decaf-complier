package decaf.backend.dataflow;

import decaf.lowlevel.instr.*;
import decaf.lowlevel.tac.TacInstr;

import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Perform liveness analysis on a control flow graph.
 *
 * @param <I> type of instructions in the control flow graph
 */
public class LivenessAnalyzer<I extends PseudoInstr> implements Consumer<CFG<I>> {

    @Override
    public void accept(CFG<I> graph) {
        for (var bb : graph.nodes) {
            computeDefAndLiveUseFor(bb);
            bb.liveIn = new TreeSet<>();
            bb.liveIn.addAll(bb.liveUse);
            bb.liveOut = new TreeSet<>();
        }

        var changed = true;
        do {
            changed = false;
            for (var bb : graph.nodes) {
                for (var next : graph.getSucc(bb.id)) {
                    bb.liveOut.addAll(graph.getBlock(next).liveIn);
                }
                bb.liveOut.removeAll(bb.def);
                if (bb.liveIn.addAll(bb.liveOut)) {
                    changed = true;
                }
                for (var next : graph.getSucc(bb.id)) {
                    bb.liveOut.addAll(graph.getBlock(next).liveIn);
                }
            }
        } while (changed);

        for (var bb : graph.nodes) {
            analyzeLivenessForEachLocIn(bb);
        }
    }

    /**
     * Compute the {@code def} and {@code liveUse} set for basic block {@code bb}.
     * <p>
     * Recall the definition:
     * - {@code def}: set of all variables (i.e. temps) that are assigned to a value. Thus, we simply union all the
     * written temps of every instruction.
     * - {@code liveUse}: set of all variables (i.e. temps) that are used before they are assigned to a value in this
     * basic block. Note this is NOT simply equal to the union set all read temps, but only those are not yet
     * assigned/reassigned.
     *
     * @param bb basic block
     */
    private void computeDefAndLiveUseFor(BasicBlock<I> bb) {
        bb.def = new TreeSet<>();
        bb.liveUse = new TreeSet<>();

        for (var loc : bb) {
            for (var read : loc.instr.getRead()) {
                if (!bb.def.contains(read)) {
                    // used before being assigned to a value
                    bb.liveUse.add(read);
                }
            }
            bb.def.addAll(loc.instr.getWritten());
        }
    }

    /**
     * Perform liveness analysis for every single location in a basic block, so that we know at each program location,
     * which variables stay alive.
     * <p>
     * Idea: realizing that every location loc can be regarded as a "mini" basic block -- a block containing that
     * instruction solely, then the data flow equations also hold, and the situation becomes much simpler:
     * - loc.liveOut = loc.next.liveIn
     * - loc.def is simply the set of written temps
     * - loc.liveUse is simply the set of read temps, since it is impossible to read and write a same temp
     * simultaneously
     * So you see, to back propagate every location solves the problem.
     *
     * @param bb the basic block
     */
    private void analyzeLivenessForEachLocIn(BasicBlock<I> bb) {
        var liveOut = new TreeSet<>(bb.liveOut);
        var it = bb.backwardIterator();

        while (it.hasNext()) {
            boolean useful = false;
            var loc = it.next();
            loc.liveOut = new TreeSet<>(liveOut);

            if (loc.instr.dsts.length != 0) {
              for (var w : loc.instr.getWritten()) if (loc.liveOut.contains(w)) { useful = true; break; }
            } else { useful = true;}

            if (!useful) {
              loc.modified = true;
              if (loc.instr instanceof TacInstr.DirectCall || loc.instr instanceof TacInstr.IndirectCall) {
                loc.instr.dsts = new Temp[]{};
                loc.modified = false;
                useful = true;
              }
            }

            liveOut.removeAll(loc.instr.getWritten());
            if (useful) liveOut.addAll(loc.instr.getRead());
            loc.liveIn = new TreeSet<>(liveOut);
        }
    }
}
