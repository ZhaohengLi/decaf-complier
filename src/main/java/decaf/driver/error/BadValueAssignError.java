package decaf.driver.error;

import decaf.frontend.tree.Pos;

public class BadValueAssignError extends DecafError {
  public String name;
  @Override
  protected String getErrMsg() {
    return "cannot assign value to class member method '" + name + "'";
  }
  public BadValueAssignError(String name, Pos pos) {
    super(pos);
    this.name = name;
  }


}
