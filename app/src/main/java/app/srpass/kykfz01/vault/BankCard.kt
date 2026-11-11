package app.srpass.kykfz01.vault

import java.util.UUID

/**
 * The two kinds of bank card we store. The difference is only meaningful for the
 * list badge and the editor's initial presentation -- both share the exact same
 * field set, so a single model with a [type] discriminator is enough.
 */
enum class CardType { CREDIT, DEBIT }

/**
 * A bank card record (credit or debit).
 *
 * Every field beyond [type] is optional: the user may store only what they want
 * (a bare bank name, or the full set including PIN and online-banking login).
 *
 * Sensitive values ([cardNumber], [cvv], [pin6], [olbAccount], [olbPassword])
 * are persisted encrypted inside the KDBX (see [app.srpass.kykfz01.vault.VaultRepository]).
 */
data class BankCard(
    val uuid: UUID,
    val type: CardType = CardType.DEBIT,
    val nickname: String = "",       // 备注名，如"工资卡"
    val bankName: String = "",        // 银行名称（自由填写，不内置列表）
    val cardNumber: String = "",      // 卡号
    val cardHolder: String = "",      // 持卡人 / 户名
    val expiry: String = "",          // 有效期 MM/YY（信用卡与储蓄卡都有到期日）
    val cvv: String = "",             // 安全码
    val pin6: String = "",            // 6 位密码 / 取款密码
    val olbAccount: String = "",      // 网银账号
    val olbPassword: String = "",     // 网银密码
    val notes: String = "",
    val updatedAt: Long = 0L,
    /**
     * 放进隐藏空间的标记。
     *
     * 和笔记一样写在自己的 KDBX 条目里，随整库备份走：换手机恢复回来卡片还是
     * 隐藏的，不会变成明文躺在列表里。
     */
    val hidden: Boolean = false
)
