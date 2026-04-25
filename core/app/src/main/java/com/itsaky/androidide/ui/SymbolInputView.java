/*
 * This file is part of AndroidIDE.
 *
 * AndroidIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
package com.itsaky.androidide.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.itsaky.androidide.R;
import com.itsaky.androidide.adapters.SymbolInputAdapter;
import com.itsaky.androidide.editor.ui.IDEEditor;
import com.itsaky.androidide.models.Symbol;
import com.itsaky.androidide.utils.Symbols;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

public class SymbolInputView extends FrameLayout {

  private static final int COLLAPSED_PAGE_HEIGHT_DP = 112;
  private static final int EXPANDED_PAGE_HEIGHT_DP = 280;
  private static final float TAB_REVEAL_START_OFFSET = 0.12f;

  private final TabLayout tabLayout;
  private final ViewPager2 pager;
  private final SymbolPagerAdapter pagerAdapter;
  private TabLayoutMediator mediator;

  public SymbolInputView(Context context) {
    this(context, null);
  }

  public SymbolInputView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SymbolInputView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    LayoutInflater.from(context).inflate(R.layout.layout_symbol_input_view, this, true);
    tabLayout = findViewById(R.id.symbol_tabs);
    pager = findViewById(R.id.symbol_pager);
    pager.setOffscreenPageLimit(2);
    pagerAdapter = new SymbolPagerAdapter();
    pager.setAdapter(pagerAdapter);
    onBottomSheetSlide(0f);
  }

  public void refresh(IDEEditor editor, List<Symbol> symbols) {
    List<Symbol> dynamicSymbols = symbols;
    if (dynamicSymbols == null || dynamicSymbols.isEmpty()) {
      dynamicSymbols = Symbols.INSTANCE.getPlainTextSymbols();
    }

    List<SymbolTabSpec> configuredTabs = loadTabSpecsFromJson();
    List<SymbolTabSpec> merged = new ArrayList<>();
    merged.add(new SymbolTabSpec("常用", dynamicSymbols));
    merged.addAll(configuredTabs);

    pagerAdapter.refresh(editor, merged);

    if (mediator != null) {
      mediator.detach();
    }
    mediator = new TabLayoutMediator(tabLayout, pager, (tab, position) -> tab.setText(merged.get(position).title));
    mediator.attach();
  }

  public void onBottomSheetSlide(float sheetOffset) {
    float clamped = Math.max(0f, Math.min(1f, sheetOffset));
    float reveal = Math.max(0f, (clamped - TAB_REVEAL_START_OFFSET) / (1f - TAB_REVEAL_START_OFFSET));

    tabLayout.setVisibility(reveal <= 0f ? View.GONE : View.VISIBLE);
    tabLayout.setAlpha(reveal);
    tabLayout.setScaleX(0.92f + (0.08f * reveal));
    tabLayout.setScaleY(0.92f + (0.08f * reveal));

    int collapsedHeight = dpToPx(COLLAPSED_PAGE_HEIGHT_DP);
    int expandedHeight = dpToPx(EXPANDED_PAGE_HEIGHT_DP);
    int targetHeight = (int) (collapsedHeight + (expandedHeight - collapsedHeight) * clamped);

    ViewGroup.LayoutParams params = pager.getLayoutParams();
    if (params.height != targetHeight) {
      params.height = targetHeight;
      pager.setLayoutParams(params);
    }
  }

  public void endItemAnimations() {
    pagerAdapter.endItemAnimations();
  }

  private int dpToPx(int dp) {
    return Math.round(dp * getResources().getDisplayMetrics().density);
  }

  private List<SymbolTabSpec> loadTabSpecsFromJson() {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                getResources().openRawResource(R.raw.symbol_input_tabs), StandardCharsets.UTF_8))) {
      StringBuilder builder = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        builder.append(line);
      }

      JSONObject root = new JSONObject(builder.toString());
      JSONArray tabs = root.optJSONArray("tabs");
      if (tabs == null) {
        return new ArrayList<>();
      }

      List<SymbolTabSpec> result = new ArrayList<>();
      for (int i = 0; i < tabs.length(); i++) {
        JSONObject tabObject = tabs.optJSONObject(i);
        if (tabObject == null) {
          continue;
        }

        String title = tabObject.optString("title", "Tab " + (i + 1));
        JSONArray symbolsArray = tabObject.optJSONArray("symbols");
        List<Symbol> parsedSymbols = new ArrayList<>();
        if (symbolsArray != null) {
          for (int s = 0; s < symbolsArray.length(); s++) {
            JSONObject symbolObject = symbolsArray.optJSONObject(s);
            if (symbolObject == null) {
              continue;
            }

            String label = symbolObject.optString("label", "");
            if (label.isEmpty()) {
              continue;
            }

            String commit = symbolObject.has("commit") ? symbolObject.optString("commit", label) : label;
            int offset = symbolObject.has("offset") ? symbolObject.optInt("offset", commit.length()) : commit.length();
            parsedSymbols.add(new Symbol(label, commit, offset));
          }
        }

        if (!parsedSymbols.isEmpty()) {
          result.add(new SymbolTabSpec(title, parsedSymbols));
        }
      }
      return result;
    } catch (Throwable ignored) {
      return new ArrayList<>();
    }
  }

  private static final class SymbolTabSpec {
    final String title;
    final List<Symbol> symbols;

    SymbolTabSpec(String title, List<Symbol> symbols) {
      this.title = title;
      this.symbols = symbols;
    }
  }

  private static final class SymbolPagerAdapter extends RecyclerView.Adapter<SymbolPagerAdapter.PageVH> {

    private final List<SymbolTabSpec> tabs = new ArrayList<>();
    private IDEEditor editor;

    void refresh(IDEEditor editor, List<SymbolTabSpec> newTabs) {
      this.editor = Objects.requireNonNull(editor);
      tabs.clear();
      tabs.addAll(newTabs);
      notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      RecyclerView recyclerView = new RecyclerView(parent.getContext());
      recyclerView.setLayoutParams(
          new RecyclerView.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      recyclerView.setNestedScrollingEnabled(true);
      recyclerView.setOverScrollMode(OVER_SCROLL_NEVER);
      recyclerView.setLayoutManager(new GridLayoutManager(parent.getContext(), 8));
      return new PageVH(recyclerView);
    }

    @Override
    public void onBindViewHolder(@NonNull PageVH holder, int position) {
      holder.recyclerView.setAdapter(new SymbolInputAdapter(editor, tabs.get(position).symbols));
    }

    @Override
    public int getItemCount() {
      return tabs.size();
    }

    void endItemAnimations() {
      for (PageVH holder : new ArrayList<>(holders)) {
        RecyclerView.ItemAnimator animator = holder.recyclerView.getItemAnimator();
        if (animator != null) {
          animator.endAnimations();
        }
      }
    }

    private final List<PageVH> holders = new ArrayList<>();

    @Override
    public void onViewAttachedToWindow(@NonNull PageVH holder) {
      super.onViewAttachedToWindow(holder);
      if (!holders.contains(holder)) {
        holders.add(holder);
      }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull PageVH holder) {
      super.onViewDetachedFromWindow(holder);
      holders.remove(holder);
    }

    private static final class PageVH extends RecyclerView.ViewHolder {
      final RecyclerView recyclerView;

      PageVH(@NonNull RecyclerView itemView) {
        super(itemView);
        this.recyclerView = itemView;
      }
    }
  }
}
