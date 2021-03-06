package tk.ardentbot.utils.updaters

import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.exceptions.PermissionException
import tk.ardentbot.core.executor.BaseCommand
import tk.ardentbot.rethink.Database.connection
import tk.ardentbot.rethink.Database.r
import tk.ardentbot.rethink.models.GuildModel
import tk.ardentbot.utils.discord.GuildUtils
import java.time.Instant

class RankableDaemon : Runnable {
    override fun run() {
        val guilds = BaseCommand.queryAsArrayList(GuildModel::class.java, r.table("guilds").run(connection))
        guilds.forEach({
            guildModel ->
            val guild: Guild = GuildUtils.getShard(guildModel.guild_id.toInt()).jda.getGuildById(guildModel.guild_id) ?: return
            guildModel.role_permissions.forEach({
                rolePermission ->
                val rankable = rolePermission.rankable ?: return
                val roleToAdd = guild.getRoleById(rankable.roleId) ?: return
                rankable.queued.forEach({
                    queued ->
                    if ((Instant.now().epochSecond - queued.value) <= rankable.secondsToWait) {
                        val user = guild.jda.getUserById(queued.key)
                        rankable.queued.remove(queued.key)
                        try {
                            val member = guild.getMember(user)
                            guild.controller.addRolesToMember(member, roleToAdd).queue()
                            guild.publicChannel.sendMessage("${user.name} has ranked up to **${roleToAdd.name}**!").queue()
                        } catch (e: PermissionException) {
                            guild.publicChannel.sendMessage("I cannot promote ${user.name} to **${roleToAdd.name}** because I don't have " +
                                    "permission to add roles!").queue()
                        }
                    }
                })
            })
        })
    }
}
