package cn.luzhou;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.containers.*;
import org.testcontainers.utility.DockerImageName;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.luzhou.store.*;
import cn.luzhou.room.Room;
import cn.luzhou.game.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class PersistenceTest {
    @Container static MySQLContainer<?> mysql=new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));
    @Container static GenericContainer<?> redis=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @Test void sqlSnapshotAuditAndRedisRecovery() {
        var ds=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        var factory=new LettuceConnectionFactory(redis.getHost(),redis.getMappedPort(6379));factory.afterPropertiesSet();factory.start();
        try {
            var cache=new StringRedisTemplate(factory);var db=new JdbcTemplate(ds);
            var store=new JdbcRedisStore(db,cache,new ObjectMapper(),new DataSourceTransactionManager(ds));
            var id=new StateStore.Identity("test-user","secret","测试");store.saveIdentity(id);assertEquals(id,store.authenticate("secret").orElseThrow());
            Room room=new Room();room.id="integration-room";room.players=new ArrayList<>(List.of("a","b","c","d"));room.gameSerial=1;
            room.game=Game.deal(new Random(1),null,ScoreRuleConfig.low());store.save(room);store.save(room);
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM play_records",Integer.class));
            assertEquals(4,db.queryForObject("SELECT COUNT(*) FROM player_results",Integer.class));
            cache.delete("room:"+room.id);Room recovered=store.load(room.id);recovered.game.assertConservation();
            assertEquals(room.game.hands,recovered.game.hands);assertEquals(room.game.turn,recovered.game.turn);
        } finally {factory.destroy();}
    }
}
