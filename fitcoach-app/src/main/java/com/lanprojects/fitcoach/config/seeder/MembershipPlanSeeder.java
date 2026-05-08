package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.membership.entity.MembershipPlan;
import com.lanprojects.fitcoach.membership.repository.MembershipPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 5 套会员套餐播种：日卡 / 周卡 / 月卡 / 季卡 / 年卡。
 *
 * <p>定价（CNY 分）：6 / 18 / 45 / 118 / 288 元。
 * 海外 USD 美分预留为 null（IAP 接入前用不到，避免误用）；接入时由 admin 在后台填入与 Apple 价格档位匹配的金额。
 *
 * <p><b>幂等</b>：按 plan_code 检测，已存在跳过，不覆盖运营在 admin 端的价格调整。
 */
@Slf4j
@Order(30)
@Component
@RequiredArgsConstructor
public class MembershipPlanSeeder implements CommandLineRunner {

    private final MembershipPlanRepository planRepository;

    @Override
    public void run(String... args) {
        int inserted = 0;
        inserted += ensure("DAILY",     "日卡", 1,    600,  10, "1 天体验所有付费动作");
        inserted += ensure("WEEKLY",    "周卡", 7,   1800, 20, "7 天解锁全部动作，适合短期备赛");
        inserted += ensure("MONTHLY",   "月卡", 30,  4500, 30, "1 个月内不限次数使用全部付费动作");
        inserted += ensure("QUARTERLY", "季卡", 90, 11800, 40, "3 个月长期方案，相比月卡省 12%");
        inserted += ensure("YEARLY",    "年卡", 365, 28800, 50, "全年最划算，相当于月卡 6.4 折");

        if (inserted > 0) {
            log.info("[seeder] 会员套餐初始化完成，新增 {} 项", inserted);
        }
    }

    private int ensure(String code, String name, int days, int priceCny, int sortOrder, String desc) {
        if (planRepository.findByPlanCode(code).isPresent()) {
            return 0;
        }
        MembershipPlan plan = new MembershipPlan();
        plan.setPlanCode(code);
        plan.setDisplayName(name);
        plan.setDurationDays(days);
        plan.setPriceCny(priceCny);
        plan.setPriceUsdCents(null);   // IAP 接入前留空
        plan.setApplePriceTier(null);
        plan.setAppleProductId(null);
        plan.setGoogleProductId(null);
        plan.setDescription(desc);
        plan.setSortOrder(sortOrder);
        plan.setEnabled(true);
        planRepository.save(plan);
        log.info("[seeder] 创建套餐：{} ({}) 有效期 {} 天 ¥{}",
                name, code, days, priceCny / 100.0);
        return 1;
    }
}
