# BankPlugin

一个功能完整的我的世界银行插件，支持活期存款、定期存款、贷款等金融功能。

## 主要功能

- 活期存款：随存随取，按日计息
- 定期存款：支持周存、月存、年存三种类型
- 贷款系统：灵活的贷款期限和利率设置
- 会员体系：不同等级享受不同利率优惠
- GUI界面：直观的用户操作界面
- 完整的经济系统集成

## PlaceholderAPI 变量

本插件提供以下 PlaceholderAPI 变量，所有变量都使用 `%bank_` 前缀：

### 基础信息
- `%bank_name%` - 玩家名称
- `%bank_balance%` - 活期存款余额
- `%bank_cash%` - 现金余额
- `%bank_total_balance%` - 总资产（活期+定期）

### 会员信息
- `%bank_level%` - 会员等级
- `%bank_bonus%` - 会员加息比例

### 利率信息
- `%bank_demand_rate%` - 活期存款基准利率
- `%bank_actual_demand_rate%` - 活期存款实际利率（含会员加息）
- `%bank_week_rate%` - 七日定期年化利率
- `%bank_month_rate%` - 月存定期年化利率
- `%bank_year_rate%` - 年存定期年化利率

### 定期存款
- `%bank_time_deposit%` - 定期存款金额
- `%bank_time_deposit_days%` - 定期存款天数
- `%bank_time_deposit_period%` - 定期存款类型（周存/月存/年存）

### 贷款信息
- `%bank_loan_amount%` - 当前贷款金额
- `%bank_loan_date%` - 贷款日期
- `%bank_loan_days%` - 剩余还款天数
- `%bank_loan_rate%` - 贷款利率（已计算会员优惠）

### 变量运算
GUI 配置中支持基础的加减运算，例如：
- `%bank_demand_rate%+%bank_bonus%%` - 计算实际利率
- `%bank_loan_rate%-%bank_bonus%%` - 计算优惠后的贷款利率

## 安装说明

1. 将插件放入服务器的 plugins 文件夹
2. 重启服务器或使用 /reload 命令
3. 配置 config.yml 文件
4. 确保已安装 PlaceholderAPI 和 Vault 前置插件

## 命令权限

### 玩家命令
- `/bank` - 打开银行GUI界面
- `/bank balance` - 查看账户余额
- `/bank deposit <金额>` - 存入活期账户
- `/bank withdraw <金额>` - 从活期账户取款
- `/bank timedeposit <金额> <存期>` - 存入定期账户
- `/bank timewithdraw` - 取出定期账户
- `/bank rates` - 查看利率
- `/bank loan <金额>` - 申请贷款
- `/bank repay <金额>` - 还款
- `/bank confirm` - 确认贷款
- `/bank logs` - 查看交易记录
- `/bank help` - 查看帮助
- `/bank admin` - 管理员命令 [还没做完]

### 管理员命令
- `/bank reload` - 重载配置文件
- `/bank admin set <玩家> <金额>` - 设置玩家余额
- `/bank admin rate <类型> <利率>` - 设置利率

## 配置文件

详细的配置说明请参考 [配置文档](config.md)

## 支持与反馈

如有问题或建议，请通过以下方式联系我们：
- 提交 GitHub Issue
- 加入QQ交流群：等一下
- 发送邮件至：等一下

## 许可证

本插件采用 MIT 许可证 