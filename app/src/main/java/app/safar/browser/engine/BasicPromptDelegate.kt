package app.safar.browser.engine

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse

/**
 * الحد الأدنى من النوافذ المنبثقة لتعمل المواقع: alert / confirm / prompt / قوائم <select>.
 * (منتقي الملفات والتاريخ والألوان في مرحلة لاحقة — انظر docs/MILESTONES.md)
 */
class BasicPromptDelegate(private val activity: () -> Activity?) : PromptDelegate {

    override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt): GeckoResult<PromptResponse>? {
        val a = activity() ?: return null
        val r = GeckoResult<PromptResponse>()
        AlertDialog.Builder(a).setTitle(prompt.title).setMessage(prompt.message)
            .setPositiveButton(android.R.string.ok) { _, _ -> r.complete(prompt.dismiss()) }
            .setOnCancelListener { r.complete(prompt.dismiss()) }
            .show()
        return r
    }

    override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt): GeckoResult<PromptResponse>? {
        val a = activity() ?: return null
        val r = GeckoResult<PromptResponse>()
        AlertDialog.Builder(a).setTitle(prompt.title).setMessage(prompt.message)
            .setPositiveButton(android.R.string.ok) { _, _ -> r.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE)) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> r.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE)) }
            .setOnCancelListener { r.complete(prompt.dismiss()) }
            .show()
        return r
    }

    override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt): GeckoResult<PromptResponse>? {
        val a = activity() ?: return null
        val r = GeckoResult<PromptResponse>()
        val input = EditText(a).apply { setText(prompt.defaultValue) }
        AlertDialog.Builder(a).setTitle(prompt.title).setMessage(prompt.message).setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> r.complete(prompt.confirm(input.text.toString())) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> r.complete(prompt.dismiss()) }
            .setOnCancelListener { r.complete(prompt.dismiss()) }
            .show()
        return r
    }

    override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptResponse>? {
        val a = activity() ?: return null
        val r = GeckoResult<PromptResponse>()
        val flat = mutableListOf<PromptDelegate.ChoicePrompt.Choice>()
        fun walk(list: Array<PromptDelegate.ChoicePrompt.Choice>) {
            list.forEach { c -> if (c.items != null) walk(c.items!!) else if (!c.separator) flat += c }
        }
        walk(prompt.choices)
        val labels = flat.map { it.label }.toTypedArray()
        val b = AlertDialog.Builder(a).setTitle(prompt.title)
        if (prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
            val checked = flat.map { it.selected }.toBooleanArray()
            b.setMultiChoiceItems(labels, checked) { _, i, v -> checked[i] = v }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    r.complete(prompt.confirm(flat.filterIndexed { i, _ -> checked[i] }.toTypedArray()))
                }
        } else {
            b.setItems(labels) { _, i -> r.complete(prompt.confirm(flat[i])) }
        }
        b.setOnCancelListener { r.complete(prompt.dismiss()) }.show()
        return r
    }
}
