package gottschlinger.howlman.cli;

import gottschlinger.howlman.gui.GuiLauncher;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "gui", description = "Launch the HowlMan graphical interface")
public class GuiCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        GuiLauncher.main(new String[]{});
        return 0;
    }
}
