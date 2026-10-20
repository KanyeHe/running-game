package cn.luzhou;

import org.junit.jupiter.api.Test;

class RulesTest {
  @Test
  void confirmedRulesAndPolicies() {
    CoreChecks.rules();
  }

  @Test
  void stateMachineAndLedger() {
    CoreChecks.state();
  }

  @Test
  void seededSimulation() {
    CoreChecks.simulations(50);
  }
}
