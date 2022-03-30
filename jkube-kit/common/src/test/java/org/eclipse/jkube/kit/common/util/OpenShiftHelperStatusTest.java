package org.eclipse.jkube.kit.common.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class OpenShiftHelperStatusTest {
  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        {"IsCancelledTrue", (BooleanSupplier) () -> OpenshiftHelper.isCancelled("Cancelled"), true},
        {"IsCancelledFalse", (BooleanSupplier) () -> OpenshiftHelper.isCancelled("not Cancelled"), false},
        {"IsFailedTrueWithFail", (BooleanSupplier) () -> OpenshiftHelper.isFailed("Fail"), true},
    });
  }

  @Parameterized.Parameter
  public String description;
  @Parameterized.Parameter (1)
  public BooleanSupplier openShiftStatusSupplier;
  @Parameterized.Parameter (2)
  public boolean expectedValue;

  @Test
  public void testUsingParametrizedTest() {
    assertThat(openShiftStatusSupplier.getAsBoolean())
        .isEqualTo(expectedValue);
  }
}