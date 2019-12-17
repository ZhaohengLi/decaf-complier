package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * example：overriding variable is not allowed for var 'kittyboy'<br>
 * PA2
 */
public class InsAbsClassError extends DecafError {

      @Override
      protected String getErrMsg() {
        System.out.println("form transform");
          return "cannot instantiate abstract class '" + name + "'";
      }

    private String name;

    public InsAbsClassError(Pos pos, String name) {
        super(pos);
        this.name = name;
        System.out.println("form error");
    }

}
