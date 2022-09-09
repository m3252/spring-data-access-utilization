package hello.springtx.propagation;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;

@Slf4j
@SpringBootTest
public class BasicTxTest {

    @Autowired
    PlatformTransactionManager txManager;

    @TestConfiguration
    static class Config {
        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @Test
    void commit() {
        log.info("트랜잭션 시작");
        TransactionStatus status = txManager.getTransaction(new DefaultTransactionDefinition());

        log.info("트랜잭션 커밋 시작");
        txManager.commit(status);
        log.info("트랜잭션 커밋 종료");
    }

    @Test
    void rollback() {
        log.info("트랜잭션 시작");
        TransactionStatus status = txManager.getTransaction(new DefaultTransactionDefinition());

        log.info("트랜잭션 롤백 시작");
        txManager.rollback(status);
        log.info("트랜잭션 롤백 종료");
    }

    @Test
    void double_commit() {
        log.info("트랜잭션1 시작");
        TransactionStatus tx1 = txManager.getTransaction(new DefaultTransactionDefinition());
        log.info("트랜잭션1 커밋 시작");
        txManager.commit(tx1);

        log.info("트랜잭션2 시작");
        TransactionStatus tx2 = txManager.getTransaction(new DefaultTransactionDefinition());
        log.info("트랜잭션2 커밋 시작");
        txManager.commit(tx2);
        log.info("트랜잭션2 커밋 종료");
    }

    @Test
    void double_commit_rollback() {
        log.info("트랜잭션1 시작");
        TransactionStatus tx1 = txManager.getTransaction(new DefaultTransactionDefinition());
        log.info("트랜잭션1 커밋 시작");
        txManager.commit(tx1);


        log.info("트랜잭션2 시작");
        TransactionStatus tx2 = txManager.getTransaction(new DefaultTransactionDefinition());
        log.info("트랜잭션2 커밋 시작");
        txManager.rollback(tx2);
        log.info("트랜잭션2 커밋 종료");
    }

    @Test
    void inner_commit() {
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("outer.isNewTransaction()={}", outer.isNewTransaction());

        log.info("내부 트랜잭션 시작");
        TransactionStatus inner = txManager.getTransaction(new DefaultTransactionAttribute());
        //Participating in existing transaction
        log.info("inner.isNewTransaction()={}", inner.isNewTransaction());
        log.info("내부 트랜잭션 커밋");
        txManager.commit(inner); // 물리적으로 커밋을 하지 않는다.

        log.info("외부 트랜잭션 커밋");
        txManager.commit(outer);
        //Initiating transaction commit
        //Committing JDBC transaction on Connection [HikariProxyConnection@1225628737 wrapping conn0: url=jdbc:h2:mem:f3d00d09-8fdb-40c5-b43c-3e1bc2fd9bd5 user=SA]
        //Releasing JDBC Connection [HikariProxyConnection@1225628737 wrapping conn0: url=jdbc:h2:mem:f3d00d09-8fdb-40c5-b43c-3e1bc2fd9bd5 user=SA] after transaction
    }

    @Test
    void outer_rollback() {
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = txManager.getTransaction(new DefaultTransactionAttribute());

        log.info("내부 트랜잭션 시작");
        TransactionStatus inner = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("내부 트랜잭션 커밋");
        txManager.commit(inner);

        log.info("외부 트랜잭션 롤백");
        txManager.rollback(outer);
    }

    @Test
    @DisplayName("트랜잭션 동기화 매니저에 rollback-only 마킹")
    void inner_rollback() {
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = txManager.getTransaction(new DefaultTransactionAttribute());

        log.info("내부 트랜잭션 시작");
        TransactionStatus inner = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("내부 트랜잭션 롤백");
        txManager.rollback(inner);
        //Participating transaction failed - marking existing transaction as rollback-only
        //Setting JDBC transaction [HikariProxyConnection@1225628737 wrapping conn0: url=jdbc:h2:mem:a7cac550-ae57-40b3-960c-f05acf5f4bf8 user=SA] rollback-only

        log.info("외부 트랜잭션 커밋");
        Assertions.assertThatThrownBy(() -> txManager.commit(outer))
                .isInstanceOf(UnexpectedRollbackException.class);
        //Global transaction is marked as rollback-only but transactional code requested commit
        //Initiating transaction rollback
        //Rolling back JDBC transaction on Connection [HikariProxyConnection@1225628737 wrapping conn0: url=jdbc:h2:mem:a7cac550-ae57-40b3-960c-f05acf5f4bf8 user=SA]
    }

    @Test
    void inner_rollback_requires_new() {
        log.info("외부 트랜적션 시작");
        TransactionStatus outer = txManager.getTransaction(new DefaultTransactionAttribute());
        log.info("outer.isNewTransaction() = {}", outer.isNewTransaction());

        innerLogic();

        log.info("외부 트랜잭션 커밋");
        txManager.commit(outer);
    }

    private void innerLogic() {
        log.info("내부 트랜적션 시작");
        DefaultTransactionAttribute definition = new DefaultTransactionAttribute();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);//기존 트랜잭션을 무시하고 신규 트랜잭션 생성
        //Suspending current transaction, creating new transaction with name [null]
        //Acquired Connection [HikariProxyConnection@1079124964 wrapping conn1
        TransactionStatus inner = txManager.getTransaction(definition);
        log.info("inner.isNewTransaction() = {}", inner.isNewTransaction());

        log.info("내부 트랜잭션 롤백");
        txManager.rollback(inner);
    }

}
