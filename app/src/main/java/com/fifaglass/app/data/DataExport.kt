package com.fifaglass.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object DataExport {

    fun exportTeamsCsv(context: Context, teams: List<Team>): Uri? {
        val sb = StringBuilder()
        sb.append("Rank,Code,Name,Confederation,Points,PrevRank,RankChange\n")
        teams.forEach { t ->
            sb.append("${t.rank},${t.code},\"${t.name}\",${t.confederation},${t.points},${t.prevRank},${t.rankChange}\n")
        }
        return writeAndShare(context, "fifa_rankings.csv", sb.toString(), "text/csv")
    }

    fun exportTeamsJson(context: Context, teams: List<Team>): Uri? {
        val sb = StringBuilder()
        sb.append("[\n")
        teams.forEachIndexed { i, t ->
            sb.append("  {\n")
            sb.append("    \"rank\": ${t.rank},\n")
            sb.append("    \"code\": \"${t.code}\",\n")
            sb.append("    \"name\": \"${t.name}\",\n")
            sb.append("    \"confederation\": \"${t.confederation}\",\n")
            sb.append("    \"points\": ${t.points},\n")
            sb.append("    \"prevRank\": ${t.prevRank},\n")
            sb.append("    \"rankChange\": ${t.rankChange}\n")
            sb.append("  }")
            if (i < teams.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]\n")
        return writeAndShare(context, "fifa_rankings.json", sb.toString(), "application/json")
    }

    fun exportMatchesCsv(context: Context, matches: List<MatchInfo>): Uri? {
        val sb = StringBuilder()
        sb.append("Date,Competition,Home,Away,HomeScore,AwayScore,Status\n")
        matches.forEach { m ->
            sb.append("${m.date},${m.competition},${m.homeName},${m.awayName},")
            sb.append("${m.homeScore ?: ""},${m.awayScore ?: ""},${m.status}\n")
        }
        return writeAndShare(context, "fifa_matches.csv", sb.toString(), "text/csv")
    }

    fun exportPredictionHistoryCsv(context: Context, records: List<PredictionRecord>): Uri? {
        val sb = StringBuilder()
        sb.append("Time,Home,Away,pHome,pDraw,pAway,LikelyScore,Confidence,Actual,Correct\n")
        records.forEach { r ->
            sb.append("${r.timestamp},${r.homeName},${r.awayName},")
            sb.append("${r.predictedHome},${r.predictedDraw},${r.predictedAway},")
            sb.append("${r.likelyScore},${r.confidence},${r.actualResult ?: ""},${r.isCorrect ?: ""}\n")
        }
        return writeAndShare(context, "prediction_history.csv", sb.toString(), "text/csv")
    }

    private fun writeAndShare(context: Context, fileName: String, content: String, mimeType: String): Uri? {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "导出 $fileName").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return uri
    }
}
