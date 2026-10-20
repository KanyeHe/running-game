package cn.luzhou.api;

import cn.luzhou.rules.CardCombinationFinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 集中装配待确认玩法；规则引擎本身不依赖 Spring。 */
@Configuration
public class RuleConfig {
  @Bean
  public CardCombinationFinder combinationFinder() {
    return CardCombinationFinder.demo();
  }
}
